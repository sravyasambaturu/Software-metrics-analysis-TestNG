package org.testng.internal.reflect;

import org.testng.collections.Lists;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.LinkedList;
import java.util.List;

public class ReflectionHelper {
  /**
   * @return An array of all locally declared methods or equivalent thereof
   * (such as default methods on Java 8 based interfaces that the given class
   * implements).
   */
  public static Method[] getLocalMethods(Class<?> clazz) {
    Method[] result;
    Method[] declaredMethods = excludingMain(clazz);
    List<Method> defaultMethods = getDefaultMethods(clazz);
    if (defaultMethods != null) {
      result = new Method[declaredMethods.length + defaultMethods.size()];
      System.arraycopy(declaredMethods, 0, result, 0, declaredMethods.length);
      int index = declaredMethods.length;
      for (Method defaultMethod : defaultMethods) {
        result[index] = defaultMethod;
        index++;
      }
    }
    else {
      List<Method> prunedMethods = Lists.newArrayList();
      for (Method declaredMethod : declaredMethods) {
        if (!declaredMethod.isBridge()) {
          prunedMethods.add(declaredMethod);
        }
      }
      result = prunedMethods.toArray(new Method[prunedMethods.size()]);
    }
    return result;
  }

  /**
   * @return An array of all locally declared methods or equivalent thereof
   * (such as default methods on Java 8 based interfaces that the given class
   * implements) but excludes the <code>main()</code> method alone.
   */
  public static Method[] excludingMain(Class<?> clazz) {
    Method[] declaredMethods = clazz.getDeclaredMethods();
    List<Method> pruned = new LinkedList<>();
    for (Method declaredMethod :declaredMethods) {
      if ("main".equals(declaredMethod.getName()) && isStaticVoid(declaredMethod) && acceptsStringArray(declaredMethod)) {
        continue;
      }
      pruned.add(declaredMethod);
    }
    return pruned.toArray(new Method[pruned.size()]);
  }

  private static boolean isStaticVoid(Method method) {
    return method.getReturnType().equals(void.class) && Modifier.isStatic(method.getModifiers());
  }

  private static boolean acceptsStringArray(Method method) {
    Class<?>[] paramTypes = method.getParameterTypes();
    if (paramTypes.length == 0) {
      return false;
    }
    Class<?> paramType = paramTypes[0];
    return paramType.isArray() && paramType.isInstance(new String[0]);
  }

  private static List<Method> getDefaultMethods(Class<?> clazz) {
    List<Method> result = null;
    for (Class<?> ifc : clazz.getInterfaces()) {
      for (Method ifcMethod : ifc.getMethods()) {
        if (!Modifier.isAbstract(ifcMethod.getModifiers())) {
          if (result == null) {
            result = new LinkedList<>();
          }
          result.add(ifcMethod);
        }
      }
    }
    return result;
  }

}
