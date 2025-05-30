package org.testng.internal;

import com.google.inject.internal.Sets;

import org.testng.IClass;
import org.testng.IMethodSelector;
import org.testng.IObjectFactory;
import org.testng.TestNGException;
import org.testng.TestRunner;
import org.testng.annotations.IAnnotation;
import org.testng.annotations.IFactoryAnnotation;
import org.testng.annotations.IParametersAnnotation;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.junit.IJUnitTestRunner;
import org.testng.xml.XmlTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Utility class for different class manipulations.
 */
public final class ClassHelper {
  private static final String JUNIT_TESTRUNNER= "org.testng.junit.JUnitTestRunner";

  /** The additional class loaders to find classes in. */
  private static final List<ClassLoader> m_classLoaders = new Vector<ClassLoader>();

  /** Add a class loader to the searchable loaders. */
  public static void addClassLoader(final ClassLoader loader) {
    m_classLoaders.add(loader);
  }

  /** Hide constructor. */
  private ClassHelper() {
    // Hide Constructor
  }

  public static <T> T newInstance(Class<T> clazz) {
    try {
      T instance = clazz.newInstance();

      return instance;
    }
    catch(IllegalAccessException iae) {
      throw new TestNGException("Class " + clazz.getName()
          + " does not have a no-args constructor", iae);
    }
    catch(InstantiationException ie) {
      throw new TestNGException("Cannot instantiate class " + clazz.getName(), ie);
    }
    catch(ExceptionInInitializerError eiierr) {
      throw new TestNGException("An exception occurred in static initialization of class "
          + clazz.getName(), eiierr);
    }
    catch(SecurityException se) {
      throw new TestNGException(se);
    }
  }
  
  /**
   * Tries to load the specified class using the context ClassLoader or if none,
   * than from the default ClassLoader. This method differs from the standard 
   * class loading methods in that it does not throw an exception if the class 
   * is not found but returns null instead.
   *  
   * @param className the class name to be loaded.
   *
   * @return the class or null if the class is not found.
   */
  public static Class<?> forName(final String className) {
    Vector<ClassLoader> allClassLoaders = new Vector<ClassLoader>();
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null) {
      allClassLoaders.add(contextClassLoader);
    }
    if (m_classLoaders != null) {
      allClassLoaders.addAll(m_classLoaders);
    }

    int count = 0;
    for (ClassLoader classLoader : allClassLoaders) {
      ++count;
      if (null == classLoader) {
        continue;
      }
      try {
        return classLoader.loadClass(className);
      }
      catch(Exception ex) {
        // With additional class loaders, it is legitimate to ignore ClassNotFoundException
        if(null == m_classLoaders || m_classLoaders.size() == 0) {
          logInstantiationError(className, ex);
        }
      }
    }

    try {
      return Class.forName(className);
    }
    catch(ClassNotFoundException cnfe) {
      logInstantiationError(className, cnfe);
      return null;
    }
  }

  private static void logInstantiationError(String className, Exception ex) {
    Utils.log("ClassHelper", 2, "Could not instantiate " + className + ": "
        + ex.getMessage());
  }

  /**
   * For the given class, returns the method annotated with &#64;Factory or null 
   * if none is found. This method does not search up the superclass hierarchy.
   * If more than one method is @Factory annotated, a TestNGException is thrown. 
   * @param cls The class to search for the @Factory annotation.
   * @param finder The finder (JDK 1.4 or JDK 5.0+) use to search for the annotation. 
   *
   * @return the @Factory <CODE>method</CODE> or null
   *  
   * FIXME: @Factory method must be public!
   * TODO rename this method to findDeclaredFactoryMethod
   */
  public static Method findFactoryMethod(Class<?> cls, IAnnotationFinder finder) {
    Method result = null;

    for (Method method : cls.getMethods()) {
      IAnnotation f = finder.findAnnotation(method, IFactoryAnnotation.class);

      if (null != f) {
        if (null != result) {
          throw new TestNGException(cls.getName() + ":  only one @Factory method allowed");
        }
        result = method;
      }
    }

    // If we didn't find anything, look for nested classes
//    if (null == result) {
//      Class[] subClasses = cls.getClasses();
//      for (Class subClass : subClasses) {
//        result = findFactoryMethod(subClass, finder);
//        if (null != result) {
//          break;
//        }
//      }
//    }

    // Found the method, verify that it returns an array of objects
    // TBD

    return result;
  }

  /**
   * Extract all callable methods of a class and all its super (keeping in mind
   * the Java access rules).
   *
   * @param clazz
   * @return
   */
  public static Set<Method> getAvailableMethods(Class<?> clazz) {
    Set<Method> methods = Sets.newHashSet();
    methods.addAll(Arrays.asList(clazz.getDeclaredMethods()));

    Class<?> parent = clazz.getSuperclass();
    while (Object.class != parent) {
      methods.addAll(extractMethods(clazz, parent, methods));
      parent = parent.getSuperclass();
    }

    return methods;
  }

  /**
   * @param runner
   * @return
   */
  public static IJUnitTestRunner createTestRunner(TestRunner runner) {
    try {
      IJUnitTestRunner tr= (IJUnitTestRunner) ClassHelper.forName(JUNIT_TESTRUNNER).newInstance();
      tr.setTestResultNotifier(runner);
      
      return tr;
    }
    catch(Exception ex) {
      throw new TestNGException("Cannot create JUnit runner " + JUNIT_TESTRUNNER, ex);
    }
  }
  
  private static Set<Method> extractMethods(Class<?> childClass, Class<?> clazz, 
      Set<Method> collected) {
    Set<Method> methods = Sets.newHashSet();

    Method[] declaredMethods = clazz.getDeclaredMethods();

    Package childPackage = childClass.getPackage();
    Package classPackage = clazz.getPackage();
    boolean isSamePackage = false;

    if ((null == childPackage) && (null == classPackage)) {
      isSamePackage = true;
    }
    if ((null != childPackage) && (null != classPackage)) {
      isSamePackage = childPackage.getName().equals(classPackage.getName());
    }

    for (Method method : declaredMethods) {
      int methodModifiers = method.getModifiers();
      if ((Modifier.isPublic(methodModifiers) || Modifier.isProtected(methodModifiers)) 
        || (isSamePackage && !Modifier.isPrivate(methodModifiers))) {
        if (!isOverridden(method, collected) && !Modifier.isAbstract(methodModifiers)) {
          methods.add(method);
        }
      }
    }

    return methods;
  }

  private static boolean isOverridden(Method method, Set<Method> collectedMethods) {
    Class<?> methodClass = method.getDeclaringClass();
    Class<?>[] methodParams = method.getParameterTypes();
    
    for (Method m: collectedMethods) {
      Class<?>[] paramTypes = m.getParameterTypes();
      if (method.getName().equals(m.getName())
         && methodClass.isAssignableFrom(m.getDeclaringClass())
         && methodParams.length == paramTypes.length) {
        
        boolean sameParameters = true;
        for (int i= 0; i < methodParams.length; i++) {
          if (!methodParams[i].equals(paramTypes[i])) {
            sameParameters = false;
            break;
          }
        }
        
        if (sameParameters) {
          return true;
        }
      }
    }
    
    return false;
  }
  
  public static IMethodSelector createSelector(org.testng.xml.XmlMethodSelector selector) {
    try {
      Class<?> cls = Class.forName(selector.getClassName());
      return (IMethodSelector) cls.newInstance();
    }
    catch(Exception ex) {
      throw new TestNGException("Couldn't find method selector : " + selector.getClassName(), ex);
    }
  }

  /**
   * Create an instance for the given class.
   */
  public static Object createInstance(Class<?> declaringClass,
                                      Map<Class, IClass> classes,
                                      XmlTest xmlTest,
                                      IAnnotationFinder finder,
                                      IObjectFactory objectFactory) {
    Object result;
  
    try {
  
      //
      // Any annotated constructor?
      //
      Constructor<?> constructor = findAnnotatedConstructor(finder, declaringClass);
      if (null != constructor) {
        IParametersAnnotation annotation = (IParametersAnnotation) finder.findAnnotation(constructor,
                                                                     IParametersAnnotation.class);
  
        String[] parameterNames = annotation.getValue();
        Object[] parameters = Parameters.createInstantiationParameters(constructor,
                                                          "@Parameters",
                                                          finder,
                                                          parameterNames,
                                                          xmlTest.getParameters(),
                                                          xmlTest.getSuite());
        result = objectFactory.newInstance(constructor, parameters);
      }
  
      //
      // No, just try to instantiate the parameterless constructor (or the one
      // with a String)
      //
      else {
  
        // If this class is a (non-static) nested class, the constructor contains a hidden
        // parameter of the type of the enclosing class
        Class<?>[] parameterTypes = new Class[0];
        Object[] parameters = new Object[0];
        Class<?> ec = getEnclosingClass(declaringClass);
        boolean isStatic = 0 != (declaringClass.getModifiers() & Modifier.STATIC);
  
        // Only add the extra parameter if the nested class is not static
        if ((null != ec) && !isStatic) {
          parameterTypes = new Class[] { ec };
  
          // Create an instance of the enclosing class so we can instantiate
          // the nested class (actually, we reuse the existing instance).
          IClass enclosingIClass = classes.get(ec);
          Object[] enclosingInstances;
          if (null != enclosingIClass) {
            enclosingInstances = enclosingIClass.getInstances(false);
            if ((null == enclosingInstances) || (enclosingInstances.length == 0)) {
              Object o = objectFactory.newInstance(ec.getConstructor(parameterTypes));
              enclosingIClass.addInstance(o);
              enclosingInstances = new Object[] { o };
            }
          }
          else {
            enclosingInstances = new Object[] { ec.newInstance() };
          }
          Object enclosingClassInstance = enclosingInstances[0];
  
          // Utils.createInstance(ec, classes, xmlTest, finder);
          parameters = new Object[] { enclosingClassInstance };
        } // isStatic
        Constructor<?> ct = declaringClass.getDeclaredConstructor(parameterTypes);
        result = objectFactory.newInstance(ct, parameters);
      }
    }
    catch (TestNGException ex) {
      // We need to pass this along
      throw ex;
    }
    catch (NoSuchMethodException ex) {
      result = ClassHelper.tryOtherConstructor(declaringClass);
    }
    catch (Throwable cause) {
      // Something else went wrong when running the constructor
      throw new TestNGException("An error occurred while instantiating class "
          + declaringClass.getName() + ": " + cause.getMessage(), cause);
    }
    
    if (null == result) {
      //result should not be null
      throw new TestNGException("An error occurred while instantiating class "
          + declaringClass.getName() + ". Check to make sure it can be accessed/instantiated.");
    }
  
    return result;
  }
  
  /**
   * Class.getEnclosingClass() only exists on JDK5, so reimplementing it
   * here.
   */
  private static Class<?> getEnclosingClass(Class<?> declaringClass) {
    Class<?> result = null;

    String className = declaringClass.getName();
    int index = className.indexOf("$");
    if (index != -1) {
      String ecn = className.substring(0, index);
      try {
        result = Class.forName(ecn);
      }
      catch (ClassNotFoundException e) {
        e.printStackTrace();
      }
    }

    return result;
  }

  /**
   * Find the best constructor given the parameters found on the annotation
   */
  private static Constructor<?> findAnnotatedConstructor(IAnnotationFinder finder,
                                                      Class<?> declaringClass) {
    Constructor<?>[] constructors = declaringClass.getDeclaredConstructors();

    for (int i = 0; i < constructors.length; i++) {
      Constructor<?> result = constructors[i];
      IParametersAnnotation annotation = (IParametersAnnotation)
          finder.findAnnotation(result, IParametersAnnotation.class);

      if (null != annotation) {
        String[] parameters = annotation.getValue();
        Class<?>[] parameterTypes = result.getParameterTypes();
        if (parameters.length != parameterTypes.length) {
          throw new TestNGException("Parameter count mismatch:  " + result + "\naccepts "
                                    + parameterTypes.length
                                    + " parameters but the @Test annotation declares "
                                    + parameters.length);
        }
        else {
          return result;
        }
      }
    }

    return null;
  }

  public static <T> T tryOtherConstructor(Class<T> declaringClass) {
    T result;
    try {
      // Special case for inner classes
      if (declaringClass.getModifiers() == 0) {
        return null;
      }

      Constructor<T> ctor = declaringClass.getConstructor(new Class[] { String.class });
      result = ctor.newInstance(new Object[] { "Default test name" });
    }
    catch (Exception e) {
      String message = e.getMessage();
      if ((message == null) && (e.getCause() != null)) {
        message = e.getCause().getMessage();
      }
      String error = "Could not create an instance of class " + declaringClass
      + ((message != null) ? (": " + message) : "")
        + ".\nPlease make sure it has a constructor that accepts either a String or no parameter.";
      throw new TestNGException(error);
    }
  
    return result;
  }

  /** 
   * When given a file name to form a class name, the file name is parsed and divided 
   * into segments. For example, "c:/java/classes/com/foo/A.class" would be divided
   * into 6 segments {"C:" "java", "classes", "com", "foo", "A"}. The first segment 
   * actually making up the class name is [3]. This value is saved in m_lastGoodRootIndex
   * so that when we parse the next file name, we will try 3 right away. If 3 fails we
   * will take the long approach. This is just a optimization cache value.
   */  
  private static int m_lastGoodRootIndex = -1;

  /**
   * Returns the Class object corresponding to the given name. The name may be
   * of the following form:
   * <ul>
   * <li>A class name: "org.testng.TestNG"</li>
   * <li>A class file name: "/testng/src/org/testng/TestNG.class"</li>
   * <li>A class source name: "d:\testng\src\org\testng\TestNG.java"</li>
   * </ul>
   * 
   * @param file
   *          the class name.
   * @return the class corresponding to the name specified.
   */
  public static Class<?> fileToClass(String file) {
    Class<?> result = null;
    
    if(!file.endsWith(".class") && !file.endsWith(".java")) {
      // Doesn't end in .java or .class, assume it's a class name
      result = ClassHelper.forName(file);

      if (null == result) {
        throw new TestNGException("Cannot load class from file: " + file);
      }

      return result;
    }
    
    int classIndex = file.lastIndexOf(".class");
    if (-1 == classIndex) {
      classIndex = file.lastIndexOf(".java");
//
//      if(-1 == classIndex) {
//        result = ClassHelper.forName(file);
//  
//        if (null == result) {
//          throw new TestNGException("Cannot load class from file: " + file);
//        }
//  
//        return result;
//      }
//
    }

    // Transforms the file name into a class name.
    
    // Remove the ".class" or ".java" extension.
    String shortFileName = file.substring(0, classIndex);
    
    // Split file name into segments. For example "c:/java/classes/com/foo/A"
    // becomes {"c:", "java", "classes", "com", "foo", "A"}
    String[] segments = shortFileName.split("[/\\\\]", -1);

    //
    // Check if the last good root index works for this one. For example, if the previous
    // name was "c:/java/classes/com/foo/A.class" then m_lastGoodRootIndex is 3 and we
    // try to make a class name ignoring the first m_lastGoodRootIndex segments (3). This 
    // will succeed rapidly if the path is the same as the one from the previous name.   
    //    
    if (-1 != m_lastGoodRootIndex) {
      
      // TODO use a SringBuffer here
      String className = segments[m_lastGoodRootIndex];
      for (int i = m_lastGoodRootIndex + 1; i < segments.length; i++) {
        className += "." + segments[i];
      }

      result = ClassHelper.forName(className);

      if (null != result) {
        return result;
      }
    }

    //
    // We haven't found a good root yet, start by resolving the class from the end segment 
    // and work our way up.  For example, if we start with "c:/java/classes/com/foo/A"
    // we'll start by resolving "A", then "foo.A", then "com.foo.A" until something 
    // resolves.  When it does, we remember the path we are at as "lastGoodRoodIndex".
    //
    
    // TODO CQ use a StringBuffer here 
    String className = null;
    for (int i = segments.length - 1; i >= 0; i--) {
      if (null == className) {
        className = segments[i];
      }
      else {
        className = segments[i] + "." + className;
      }

      result = ClassHelper.forName(className);

      if (null != result) {
        m_lastGoodRootIndex = i;
        break;
      }
    }

    if (null == result) {
      throw new TestNGException("Cannot load class from file: " + file);
    }

    return result;
  }

}
