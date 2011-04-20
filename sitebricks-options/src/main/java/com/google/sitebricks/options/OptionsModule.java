package com.google.sitebricks.options;

import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.sitebricks.conversion.MvelTypeConverter;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author dhanji@gmail.com (Dhanji R. Prasanna)
 */
public class OptionsModule extends AbstractModule {
  private final Map<String, String> options;

  private final List<Class<?>> optionClasses = new ArrayList<Class<?>>();

  public OptionsModule(String[] commandLine) {
    options = new HashMap<String, String>(commandLine.length);
    for (String option : commandLine) {
      if (option.startsWith("--") && option.length() > 2) {
        option = option.substring(2);

        String[] pair = option.split("=", 2);
        if (pair.length == 1) {
          options.put(pair[0], Boolean.TRUE.toString());
        } else {
          options.put(pair[0], pair[1]);
        }
      }
    }
  }

  @Override
  protected final void configure() {
    // Analyze options classes.
    for (Class<?> optionClass : optionClasses) {
      String namespace = optionClass.getAnnotation(Options.class).value();
      if (!namespace.isEmpty())
        namespace += ".";

      // Construct a map that will contain the values needed to back the interface.
      final Map<String, String> concreteOptions =
          new HashMap<String, String>(optionClass.getDeclaredMethods().length);
      boolean skipClass = false;
      for (Method method : optionClass.getDeclaredMethods()) {
        String key = namespace + method.getName();

        String value = options.get(key);

        // Gather all the errors regarding @Options methods that have no specified config.
        if (null == value) {
          addError("Option '%s' specified in type [%s] is unavailable in provided configuration", key,
              optionClass);
          skipClass = true;
          break;
        }

        // TODO Can we validate that the value is coercible into the return type correctly?
        concreteOptions.put(method.getName(), value);
      }

      if (!skipClass) {
        InvocationHandler handler = new InvocationHandler() {
          @Inject
          MvelTypeConverter converter;

          @Override
          public Object invoke(Object o, Method method, Object[] objects) throws Throwable {
            return converter.convert(concreteOptions.get(method.getName()), method.getReturnType());
          }
        };

        requestInjection(handler);
        Object instance = Proxy.newProxyInstance(Thread.currentThread().getContextClassLoader(),
            new Class<?>[]{ optionClass }, handler);
        
        bind((Class) optionClass).toInstance(instance);
      }
    }
  }

  public OptionsModule options(Class<?> clazz) {
    if (!clazz.isInterface() && !Modifier.isAbstract(clazz.getModifiers())) {
      throw new IllegalArgumentException(String.format("%s must be an interface or abstract class",
          clazz.getName()));
    }

    if (!clazz.isAnnotationPresent(Options.class)) {
      throw new IllegalArgumentException(String.format("%s must be annotated with @Options",
          clazz.getName()));
    }

    optionClasses.add(clazz);
    return this;
  }
}