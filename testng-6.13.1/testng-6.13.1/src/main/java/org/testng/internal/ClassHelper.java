package org.testng.internal;

import org.testng.IClass;
import org.testng.IMethodSelector;
import org.testng.IObjectFactory;
import org.testng.IObjectFactory2;
import org.testng.ITestObjectFactory;
import org.testng.TestNGException;
import org.testng.TestRunner;
import org.testng.annotations.IFactoryAnnotation;
import org.testng.annotations.IParametersAnnotation;
import org.testng.collections.Lists;
import org.testng.collections.Maps;
import org.testng.collections.Sets;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.internal.reflect.ReflectionHelper;
import org.testng.junit.IJUnitTestRunner;
import org.testng.log4testng.Logger;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlSuite;
import org.testng.xml.XmlTest;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * Utility class for different class manipulations.
 */
public final class ClassHelper {
  private static final String JUNIT_TESTRUNNER= "org.testng.junit.JUnitTestRunner";
  private static final String JUNIT_4_TESTRUNNER = "org.testng.junit.JUnit4TestRunner";

  /** The additional class loaders to find classes in. */
  private static final List<ClassLoader> classLoaders = new Vector<>();
  private static final String CANNOT_INSTANTIATE_CLASS = "Cannot instantiate class ";
  private static final String CLASS_HELPER = ClassHelper.class.getSimpleName();
  private static final String SKIP_CALLER_CLS_LOADER = "skip.caller.clsLoader";

  /**
   * When given a file name to form a class name, the file name is parsed and divided
   * into segments. For example, "c:/java/classes/com/foo/A.class" would be divided
   * into 6 segments {"C:" "java", "classes", "com", "foo", "A"}. The first segment
   * actually making up the class name is [3]. This value is saved in lastGoodRootIndex
   * so that when we parse the next file name, we will try 3 right away. If 3 fails we
   * will take the long approach. This is just a optimization cache value.
   */
  private static int lastGoodRootIndex = -1;

  /** Hide constructor. */
  private ClassHelper() {
    // Hide Constructor
  }

  /** Add a class loader to the searchable loaders. */
  public static void addClassLoader(final ClassLoader loader) {
    classLoaders.add(loader);
  }

  public static <T> T newInstance(Class<T> clazz) {
    try {
      return clazz.newInstance();
    } catch(IllegalAccessException | InstantiationException | ExceptionInInitializerError | SecurityException e) {
      throw new TestNGException(CANNOT_INSTANTIATE_CLASS + clazz.getName(), e);
    }
  }

  public static <T> T newInstanceOrNull(Class<T> clazz) {
    try {
      Constructor<T> constructor = clazz.getConstructor();
      return newInstance(constructor);
    } catch(ExceptionInInitializerError | SecurityException e) {
      throw new TestNGException(CANNOT_INSTANTIATE_CLASS + clazz.getName(), e);
    } catch (NoSuchMethodException e) {
      return null;
    }
  }

  public static <T> T newInstance(Constructor<T> constructor, Object... parameters) {
    try {
      return constructor.newInstance(parameters);
    } catch (InstantiationException | IllegalAccessException | InvocationTargetException e) {
      throw new TestNGException(CANNOT_INSTANTIATE_CLASS + constructor.getDeclaringClass().getName(), e);
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
    List<ClassLoader> allClassLoaders = Lists.newArrayList();
    ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
    if (contextClassLoader != null) {
      allClassLoaders.add(contextClassLoader);
    }
    allClassLoaders.addAll(classLoaders);

    for (ClassLoader classLoader : allClassLoaders) {
      if (null == classLoader) {
        continue;
      }
      try {
        return classLoader.loadClass(className);
      }
      catch(ClassNotFoundException ex) {
        // With additional class loaders, it is legitimate to ignore ClassNotFoundException
        if (classLoaders.isEmpty()) {
          logClassNotFoundError(className, ex);
        }
      }
    }
    if (Boolean.parseBoolean(System.getProperty(SKIP_CALLER_CLS_LOADER))) {
      return null;
    }

    try {
      return Class.forName(className);
    }
    catch(ClassNotFoundException cnfe) {
      logClassNotFoundError(className, cnfe);
      return null;
    }
  }

  private static void logClassNotFoundError(String className, Exception ex) {
    Utils.log(CLASS_HELPER, 2, "Could not instantiate " + className
        + " : Class doesn't exist (" + ex.getMessage() + ")");
  }

  /**
   * For the given class, returns the method annotated with &#64;Factory or null
   * if none is found. This method does not search up the superclass hierarchy.
   * If more than one method is @Factory annotated, a TestNGException is thrown.
   * @param cls The class to search for the @Factory annotation.
   * @param finder The finder (JDK 1.4 or JDK 5.0+) use to search for the annotation.
   *
   * @return the @Factory <CODE>methods</CODE>
   */
  public static List<ConstructorOrMethod> findDeclaredFactoryMethods(Class<?> cls,
                                                                     IAnnotationFinder finder) {
    List<ConstructorOrMethod> result = new ArrayList<>();

    for (Method method : getAvailableMethods(cls)) {
      IFactoryAnnotation f = finder.findAnnotation(method, IFactoryAnnotation.class);
      if (f != null) {
        ConstructorOrMethod factory = new ConstructorOrMethod(method);
        factory.setEnabled(f.getEnabled());
        result.add(factory);
      }
    }

    for (Constructor constructor : cls.getDeclaredConstructors()) {
      IFactoryAnnotation f = finder.findAnnotation(constructor, IFactoryAnnotation.class);
      if (f != null) {
        ConstructorOrMethod factory = new ConstructorOrMethod(constructor);
        factory.setEnabled(f.getEnabled());
        result.add(factory);
      }
    }

    return result;
  }

  /**
   * Extract all callable methods of a class and all its super (keeping in mind
   * the Java access rules).
   */
  public static Set<Method> getAvailableMethods(Class<?> clazz) {
    Map<String, Set<Method>> methods = Maps.newHashMap();
    for (final Method declaredMethod : ReflectionHelper.getLocalMethods(clazz)) {
      appendMethod(methods, declaredMethod);
    }

    Class<?> parent = clazz.getSuperclass();
    if (null != parent) {
      while (!Object.class.equals(parent)) {
        Set<Map.Entry<String, Set<Method>>> extractedMethods = extractMethods(clazz, parent, methods).entrySet();
        for (Map.Entry<String, Set<Method>> extractedMethod : extractedMethods) {
          Set<Method> m = methods.get(extractedMethod.getKey());
          if (m == null) {
            methods.put(extractedMethod.getKey(), extractedMethod.getValue());
          } else {
            m.addAll(extractedMethod.getValue());
          }
        }
        parent = parent.getSuperclass();
      }
    }

    Set<Method> returnValue = Sets.newHashSet();
    for (Set<Method> each : methods.values()) {
      returnValue.addAll(each);
    }
    return returnValue;
  }

  public static IJUnitTestRunner createTestRunner(TestRunner runner) {
    IJUnitTestRunner tr = null;
      try {
          //try to get runner for JUnit 4 first
        Class.forName("org.junit.Test");
        Class<?> clazz = ClassHelper.forName(JUNIT_4_TESTRUNNER);
        if (clazz != null) {
          tr = (IJUnitTestRunner) clazz.newInstance();
          tr.setTestResultNotifier(runner);
        }
      } catch (Throwable t) {
          Utils.log(CLASS_HELPER, 2, "JUnit 4 was not found on the classpath");
          try {
              //fallback to JUnit 3
              Class.forName("junit.framework.Test");
              Class<?> clazz =ClassHelper.forName(JUNIT_TESTRUNNER);
              if (clazz != null) {
                tr = (IJUnitTestRunner) clazz.newInstance();
                tr.setTestResultNotifier(runner);
              }
          } catch (Exception ex) {
              Utils.log(CLASS_HELPER, 2, "JUnit 3 was not found on the classpath");
              //there's no JUnit on the classpath
              throw new TestNGException("Cannot create JUnit runner", ex);
          }
      }
      return tr;
  }

  private static void appendMethod(Map<String, Set<Method>> methods, Method declaredMethod) {
    Set<Method> declaredMethods = methods.get(declaredMethod.getName());
    if (declaredMethods == null) {
      declaredMethods = Sets.newHashSet();
      methods.put(declaredMethod.getName(), declaredMethods);
    }
    declaredMethods.add(declaredMethod);
  }

  private static Map<String, Set<Method>> extractMethods(Class<?> childClass, Class<?> clazz,
      Map<String, Set<Method>> collected) {
    Map<String, Set<Method>> methods = Maps.newHashMap();

    Method[] declaredMethods = clazz.getDeclaredMethods();

    Package childPackage = childClass.getPackage();
    Package classPackage = clazz.getPackage();
    boolean isSamePackage = isSamePackage(childPackage, classPackage);

    for (Method method : declaredMethods) {
      if (canInclude(isSamePackage, method, collected)) {
        appendMethod(methods, method);
      }
    }

    return methods;
  }

  private static boolean canInclude(boolean isSamePackage, Method method, Map<String, Set<Method>> collected) {
    int methodModifiers = method.getModifiers();
    boolean visible = (Modifier.isPublic(methodModifiers) || Modifier.isProtected(methodModifiers))
        || (isSamePackage && !Modifier.isPrivate(methodModifiers));
    boolean hasNoInheritanceTraits = !isOverridden(method, collected) && !Modifier.isAbstract(methodModifiers);
    return visible && hasNoInheritanceTraits;
  }

  private static boolean isSamePackage(Package childPackage, Package classPackage) {
    boolean isSamePackage = false;

    if ((null == childPackage) && (null == classPackage)) {
      isSamePackage = true;
    }
    if ((null != childPackage) && (null != classPackage)) {
      isSamePackage = childPackage.getName().equals(classPackage.getName());
    }
    return isSamePackage;
  }

  private static boolean isOverridden(Method method, Map<String, Set<Method>> methodsByName) {
    Set<Method> collectedMethods = methodsByName.get(method.getName());
    if (collectedMethods == null) {
      return false;
    }
    Class<?> methodClass = method.getDeclaringClass();
    Class<?>[] methodParams = method.getParameterTypes();

    for (Method m: collectedMethods) {
      Class<?>[] paramTypes = m.getParameterTypes();
      if (methodClass.isAssignableFrom(m.getDeclaringClass()) && methodParams.length == paramTypes.length) {
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
      Map<Class<?>, IClass> classes,
      XmlTest xmlTest,
      IAnnotationFinder finder,
      ITestObjectFactory objectFactory)
  {
    if (objectFactory instanceof IObjectFactory) {
      return createInstance1(declaringClass, classes, xmlTest, finder,
          (IObjectFactory) objectFactory);
    } else if (objectFactory instanceof IObjectFactory2) {
      return createInstance2(declaringClass, (IObjectFactory2) objectFactory);
    } else {
      throw new AssertionError("Unknown object factory type:" + objectFactory);
    }
  }

  private static Object createInstance2(Class<?> declaringClass, IObjectFactory2 objectFactory) {
    return objectFactory.newInstance(declaringClass);
  }

  public static Object createInstance1(Class<?> declaringClass,
                                      Map<Class<?>, IClass> classes,
                                      XmlTest xmlTest,
                                      IAnnotationFinder finder,
                                      IObjectFactory objectFactory) {
    Object result = null;

    try {

      //
      // Any annotated constructor?
      //
      Constructor<?> constructor = findAnnotatedConstructor(finder, declaringClass);
      if (null != constructor) {
        IParametersAnnotation parametersAnnotation = finder.findAnnotation(constructor, IParametersAnnotation.class);
        if (parametersAnnotation != null) { // null if the annotation is @Factory
          String[] parameterNames = parametersAnnotation.getValue();
          Object[] parameters = Parameters.createInstantiationParameters(constructor,
                  "@Parameters",
                  finder,
                  parameterNames,
                  xmlTest.getAllParameters(),
                  xmlTest.getSuite());
          result = objectFactory.newInstance(constructor, parameters);
        }
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

          parameters = new Object[] { enclosingClassInstance };
        } // isStatic

        Constructor<?> ct;
        try {
          ct = declaringClass.getDeclaredConstructor(parameterTypes);
        }
        catch (NoSuchMethodException ex) {
          ct = declaringClass.getDeclaredConstructor(String.class);
          parameters = new Object[] { xmlTest.getName() };
          // If ct == null here, we'll pass a null
          // constructor to the factory and hope it can deal with it
        }
        result = objectFactory.newInstance(ct, parameters);
      }
    }
    catch (TestNGException ex) {
      throw ex;
    }
    catch (NoSuchMethodException ex) {
      //Empty catch block
    }
    catch (Throwable cause) {
      // Something else went wrong when running the constructor
      throw new TestNGException("An error occurred while instantiating class "
          + declaringClass.getName() + ": " + cause.getMessage(), cause);
    }

    if (result == null && ! Modifier.isPublic(declaringClass.getModifiers())) {
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
    int index = className.indexOf('$');
    if (index != -1) {
      String ecn = className.substring(0, index);
      try {
        result = Class.forName(ecn);
      }
      catch (ClassNotFoundException e) {
        Logger.getLogger(ClassHelper.class).error(e.getMessage(),e);
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

    for (Constructor<?> result : constructors) {
      IParametersAnnotation parametersAnnotation = finder.findAnnotation(result, IParametersAnnotation.class);
      if (parametersAnnotation != null) {
        String[] parameters = parametersAnnotation.getValue();
        Class<?>[] parameterTypes = result.getParameterTypes();
        if (parameters.length != parameterTypes.length) {
          throw new TestNGException("Parameter count mismatch:  " + result + "\naccepts "
                                    + parameterTypes.length
                                    + " parameters but the @Test annotation declares "
                                    + parameters.length);
        }
        return result;
      }

      IFactoryAnnotation factoryAnnotation = finder.findAnnotation(result, IFactoryAnnotation.class);
      if (factoryAnnotation != null) {
        return result;
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

      Constructor<T> ctor = declaringClass.getConstructor(String.class);
      result = ctor.newInstance("Default test name");
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

      if (file.startsWith("class ")) {
        file = file.substring("class ".length());
      }

      result = ClassHelper.forName(file);

      if (null == result) {
        throw new TestNGException("Cannot load class from file: " + file);
      }

      return result;
    }

    int classIndex = file.lastIndexOf(".class");
    if (-1 == classIndex) {
      classIndex = file.lastIndexOf(".java");
    }

    // Transforms the file name into a class name.

    // Remove the ".class" or ".java" extension.
    String shortFileName = file.substring(0, classIndex);

    // Split file name into segments. For example "c:/java/classes/com/foo/A"
    String[] segments = shortFileName.split("[/\\\\]", -1);

    //
    // Check if the last good root index works for this one. For example, if the previous
    // name was "c:/java/classes/com/foo/A.class" then lastGoodRootIndex is 3 and we
    // try to make a class name ignoring the first lastGoodRootIndex segments (3). This
    // will succeed rapidly if the path is the same as the one from the previous name.
    //
    if (-1 != lastGoodRootIndex) {

      StringBuilder className = new StringBuilder(segments[lastGoodRootIndex]);
      for (int i = lastGoodRootIndex + 1; i < segments.length; i++) {
        className.append(".").append(segments[i]);
      }

      result = ClassHelper.forName(className.toString());

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

    String className = "";
    for (int i = segments.length - 1; i >= 0; i--) {
      if (className.length() == 0) {
        className = segments[i];
      }
      else {
        className = segments[i] + "."  + className;
      }

      result = ClassHelper.forName(className);

      if (null != result) {
        lastGoodRootIndex = i;
        break;
      }
    }

    if (null == result) {
      throw new TestNGException("Cannot load class from file: " + file);
    }

    return result;
  }

  /**
   * @param cls   - The class to look for.
   * @param suite - The {@link XmlSuite} whose &lt;test&gt; tags needs to be searched in.
   * @return - All the {@link XmlClass} objects that share the same &lt;test&gt; tag as the class.
   */
  public static XmlClass[] findClassesInSameTest(Class<?> cls, XmlSuite suite) {
    Collection<XmlClass> vResult = Sets.newHashSet();
    for (XmlTest test : suite.getTests()) {
      vResult.addAll(findClassesInSameTest(cls, test));
    }

    return vResult.toArray(new XmlClass[vResult.size()]);
  }

  private static Collection<XmlClass> findClassesInSameTest(Class<?> cls, XmlTest xmlTest) {
    Collection<XmlClass> vResult = Sets.newHashSet();
    String className = cls.getName();
    for (XmlClass testClass : xmlTest.getXmlClasses()) {
      if (testClass.getName().equals(className)) {
        // Found it, add all the classes in this test in the result
        vResult.addAll(xmlTest.getXmlClasses());
        break;
      }
    }

    return vResult;
  }

}
