package org.testng;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.testng.collections.ListMultiMap;
import org.testng.collections.Lists;
import org.testng.collections.Maps;
import org.testng.collections.Sets;
import org.testng.internal.AbstractParallelWorker;
import org.testng.internal.Attributes;
import org.testng.internal.ClassHelper;
import org.testng.internal.ClassInfoMap;
import org.testng.internal.ConfigurationGroupMethods;
import org.testng.internal.DefaultListenerFactory;
import org.testng.internal.DynamicGraph;
import org.testng.internal.DynamicGraph.Status;
import org.testng.internal.DynamicGraphHelper;
import org.testng.internal.GroupsHelper;
import org.testng.internal.IConfiguration;
import org.testng.internal.IInvoker;
import org.testng.internal.ITestResultNotifier;
import org.testng.internal.InvokedMethod;
import org.testng.internal.Invoker;
import org.testng.internal.MethodGroupsHelper;
import org.testng.internal.MethodHelper;
import org.testng.internal.ResultMap;
import org.testng.internal.RunInfo;
import org.testng.internal.Systematiser;
import org.testng.internal.TestListenerHelper;
import org.testng.internal.TestMethodWorker;
import org.testng.internal.TestNGClassFinder;
import org.testng.internal.TestNGMethodFinder;
import org.testng.internal.Utils;
import org.testng.internal.XmlMethodSelector;
import org.testng.internal.annotations.IAnnotationFinder;
import org.testng.internal.thread.graph.GraphThreadPoolExecutor;
import org.testng.internal.thread.graph.IThreadWorkerFactory;
import org.testng.internal.thread.graph.IWorker;
import org.testng.junit.IJUnitTestRunner;
import org.testng.util.Strings;
import org.testng.xml.XmlClass;
import org.testng.xml.XmlInclude;
import org.testng.xml.XmlPackage;
import org.testng.xml.XmlTest;

import com.google.inject.Injector;
import com.google.inject.Module;

import static org.testng.internal.MethodHelper.fixMethodsWithClass;

/**
 * This class takes care of running one Test.
 */
public class TestRunner
    implements ITestContext, ITestResultNotifier, IThreadWorkerFactory<ITestNGMethod> {

  private static final String DEFAULT_PROP_OUTPUT_DIR = "test-output";

  private final Comparator<ITestNGMethod> comparator;
  private ISuite m_suite;
  private XmlTest m_xmlTest;
  private String m_testName;

  private final GuiceHelper guiceHelper = new GuiceHelper(this);

  private List<XmlClass> m_testClassesFromXml= null;

  private IInvoker m_invoker= null;
  private IAnnotationFinder m_annotationFinder= null;

  /** ITestListeners support. */
  private List<ITestListener> m_testListeners = Lists.newArrayList();
  private Set<IConfigurationListener> m_configurationListeners = Sets.newHashSet();

  private IConfigurationListener m_confListener= new ConfigurationListener();

  private Collection<IInvokedMethodListener> m_invokedMethodListeners = Lists.newArrayList();
  private final Map<Class<? extends IClassListener>, IClassListener> m_classListeners = Maps.newHashMap();
  private final Map<Class<? extends IDataProviderListener>, IDataProviderListener>  m_dataProviderListeners;

  /**
   * All the test methods we found, associated with their respective classes.
   * Note that these test methods might belong to different classes.
   * We pick which ones to run at runtime.
   */
  private ITestNGMethod[] m_allTestMethods = new ITestNGMethod[0];

  // Information about this test run

  private Date m_startDate = null;
  private Date m_endDate = null;

  /** A map to keep track of Class <-> IClass. */
  private Map<Class<?>, ITestClass> m_classMap = Maps.newLinkedHashMap();

  /** Where the reports will be created. */
  private String m_outputDirectory= DEFAULT_PROP_OUTPUT_DIR;

  // The XML method selector (groups/methods included/excluded in XML)
  private XmlMethodSelector m_xmlMethodSelector = new XmlMethodSelector();

  private static int m_verbose = 1;

  //
  // These next fields contain all the configuration methods found on this class.
  // At initialization time, they just contain all the various @Configuration methods
  // found in all the classes we are going to run.  When comes the time to run them,
  // only a subset of them are run:  those that are enabled and belong on the same class as
  // (or a parent of) the test class.
  //
  /** */
  private ITestNGMethod[] m_beforeSuiteMethods = {};
  private ITestNGMethod[] m_afterSuiteMethods = {};
  private ITestNGMethod[] m_beforeXmlTestMethods = {};
  private ITestNGMethod[] m_afterXmlTestMethods = {};
  private List<ITestNGMethod> m_excludedMethods = Lists.newArrayList();
  private ConfigurationGroupMethods m_groupMethods = null;

  // Meta groups
  private Map<String, List<String>> m_metaGroups = Maps.newHashMap();

  // All the tests that were run along with their result
  private IResultMap m_passedTests = new ResultMap();
  private IResultMap m_failedTests = new ResultMap();
  private IResultMap m_failedButWithinSuccessPercentageTests = new ResultMap();
  private IResultMap m_skippedTests = new ResultMap();

  private RunInfo m_runInfo= new RunInfo();

  // The host where this test was run, or null if run locally
  private String m_host;

  // Defined dynamically depending on <test preserve-order="true/false">
  private List<IMethodInterceptor> m_methodInterceptors;

  private ClassMethodMap m_classMethodMap;
  private TestNGClassFinder m_testClassFinder;
  private IConfiguration m_configuration;
  private IMethodInterceptor builtinInterceptor;

  public enum PriorityWeight {
    groupByInstance, preserveOrder, priority, dependsOnGroups, dependsOnMethods
  }

  protected TestRunner(IConfiguration configuration,
                       ISuite suite,
                       XmlTest test,
                       String outputDirectory,
                       IAnnotationFinder finder,
                       boolean skipFailedInvocationCounts,
                       Collection<IInvokedMethodListener> invokedMethodListeners,
                       List<IClassListener> classListeners, Comparator<ITestNGMethod> comparator,
                       Map<Class<? extends IDataProviderListener>, IDataProviderListener>  dataProviderListeners) {
    this.comparator = comparator;
    this.m_dataProviderListeners = Maps.newHashMap(dataProviderListeners);
    init(configuration, suite, test, outputDirectory, finder, skipFailedInvocationCounts,
            invokedMethodListeners, classListeners);
  }


  public TestRunner(IConfiguration configuration, ISuite suite, XmlTest test,
      boolean skipFailedInvocationCounts,
      Collection<IInvokedMethodListener> invokedMethodListeners,
      List<IClassListener> classListeners, Comparator<ITestNGMethod> comparator) {
    this.comparator = comparator;
    this.m_dataProviderListeners = Collections.emptyMap();
    init(configuration, suite, test, suite.getOutputDirectory(),
        suite.getAnnotationFinder(),
        skipFailedInvocationCounts, invokedMethodListeners, classListeners);
  }
  
  /**
   * This constructor is used by testng-remote, any changes related to it please contact with testng-team.
   */
  public TestRunner(IConfiguration configuration, ISuite suite, XmlTest test,
      boolean skipFailedInvocationCounts,
      Collection<IInvokedMethodListener> invokedMethodListeners,
      List<IClassListener> classListeners) {
    this.comparator = Systematiser.getComparator();
    this.m_dataProviderListeners = Collections.emptyMap();
    init(configuration, suite, test, suite.getOutputDirectory(),
        suite.getAnnotationFinder(),
        skipFailedInvocationCounts, invokedMethodListeners, classListeners);
  }

  private void init(IConfiguration configuration,
                    ISuite suite,
                    XmlTest test,
                    String outputDirectory,
                    IAnnotationFinder annotationFinder,
                    boolean skipFailedInvocationCounts,
                    Collection<IInvokedMethodListener> invokedMethodListeners,
                    List<IClassListener> classListeners)
  {
    m_configuration = configuration;
    m_xmlTest= test;
    m_suite = suite;
    m_testName = test.getName();
    m_host = suite.getHost();
    m_testClassesFromXml= test.getXmlClasses();
    setVerbose(test.getVerbose());

    boolean preserveOrder = test.getPreserveOrder();
    m_methodInterceptors = new ArrayList<>();
    builtinInterceptor = preserveOrder ? new PreserveOrderMethodInterceptor() : new InstanceOrderingMethodInterceptor();

    List<XmlPackage> m_packageNamesFromXml = test.getXmlPackages();
    if(null != m_packageNamesFromXml) {
      for(XmlPackage xp: m_packageNamesFromXml) {
        m_testClassesFromXml.addAll(xp.getXmlClasses());
      }
    }

    m_annotationFinder= annotationFinder;
    m_invokedMethodListeners = invokedMethodListeners;
    m_classListeners.clear();
    for (IClassListener classListener : classListeners) {
      m_classListeners.put(classListener.getClass(), classListener);
    }
    m_invoker = new Invoker(m_configuration, this, this, m_suite.getSuiteState(),
            skipFailedInvocationCounts, invokedMethodListeners, classListeners, m_dataProviderListeners.values());

    if (test.getParallel() != null) {
      log(3, "Running the tests in '" + test.getName() + "' with parallel mode:" + test.getParallel());
    }

    setOutputDirectory(outputDirectory);

    // Finish our initialization
    init();
  }

  public IInvoker getInvoker() {
    return m_invoker;
  }

  public ITestNGMethod[] getBeforeSuiteMethods() {
    return m_beforeSuiteMethods;
  }

  public ITestNGMethod[] getAfterSuiteMethods() {
    return m_afterSuiteMethods;
  }

  public ITestNGMethod[] getBeforeTestConfigurationMethods() {
    return m_beforeXmlTestMethods;
  }

  public ITestNGMethod[] getAfterTestConfigurationMethods() {
    return m_afterXmlTestMethods;
  }

  private void init() {
    initMetaGroups(m_xmlTest);
    initRunInfo(m_xmlTest);

    // Init methods and class map
    // JUnit behavior is different and doesn't need this initialization step
    if(!m_xmlTest.isJUnit()) {
      initMethods();
    }

    initListeners();
    addConfigurationListener(m_confListener);
    for (IConfigurationListener cl : m_configuration.getConfigurationListeners()) {
      addConfigurationListener(cl);
    }
  }

  private void initListeners() {
    //
    // Find all the listener factories and collect all the listeners requested in a
    // @Listeners annotation.
    //
    Set<Class<? extends ITestNGListener>> listenerClasses = Sets.newHashSet();
    Class<? extends ITestNGListenerFactory> listenerFactoryClass = null;

    for (IClass cls : getTestClasses()) {
      Class<?> realClass = cls.getRealClass();
      TestListenerHelper.ListenerHolder listenerHolder = TestListenerHelper.findAllListeners(realClass, m_annotationFinder);
      if (listenerFactoryClass == null) {
        listenerFactoryClass = listenerHolder.getListenerFactoryClass();
      }
      listenerClasses.addAll(listenerHolder.getListenerClasses());
    }

    if (listenerFactoryClass == null) {
      listenerFactoryClass = DefaultListenerFactory.class;
    }

    //
    // Now we have all the listeners collected from @Listeners and at most one
    // listener factory collected from a class implementing ITestNGListenerFactory.
    // Instantiate all the requested listeners.
    //

    ITestNGListenerFactory factory = TestListenerHelper.createListenerFactory(m_testClassFinder, listenerFactoryClass);

    // Instantiate all the listeners
    for (Class<? extends ITestNGListener> c : listenerClasses) {
      if (IClassListener.class.isAssignableFrom(c) && m_classListeners.containsKey(c)) {
          continue;
      }
      ITestNGListener listener = factory.createListener(c);

      addListener(listener);
    }
  }

  /**
   * Initialize meta groups
   */
  private void initMetaGroups(XmlTest xmlTest) {
    Map<String, List<String>> metaGroups = xmlTest.getMetaGroups();

    for (Map.Entry<String, List<String>> entry : metaGroups.entrySet()) {
      addMetaGroup(entry.getKey(), entry.getValue());
    }
  }

  private void initRunInfo(final XmlTest xmlTest) {
    // Groups
    m_xmlMethodSelector.setIncludedGroups(createGroups(m_xmlTest.getIncludedGroups()));
    m_xmlMethodSelector.setExcludedGroups(createGroups(m_xmlTest.getExcludedGroups()));
    m_xmlMethodSelector.setExpression(m_xmlTest.getExpression());

    // Methods
    m_xmlMethodSelector.setXmlClasses(m_xmlTest.getXmlClasses());

    m_runInfo.addMethodSelector(m_xmlMethodSelector, 10);

    // Add user-specified method selectors (only class selectors, we can ignore
    // script selectors here)
    if (null != xmlTest.getMethodSelectors()) {
      for (org.testng.xml.XmlMethodSelector selector : xmlTest.getMethodSelectors()) {
        if (selector.getClassName() != null) {
          IMethodSelector s = ClassHelper.createSelector(selector);

          m_runInfo.addMethodSelector(s, selector.getPriority());
        }
      }
    }
  }

  private void initMethods() {

    //
    // Calculate all the methods we need to invoke
    //
    List<ITestNGMethod> beforeClassMethods = Lists.newArrayList();
    List<ITestNGMethod> testMethods = Lists.newArrayList();
    List<ITestNGMethod> afterClassMethods = Lists.newArrayList();
    List<ITestNGMethod> beforeSuiteMethods = Lists.newArrayList();
    List<ITestNGMethod> afterSuiteMethods = Lists.newArrayList();
    List<ITestNGMethod> beforeXmlTestMethods = Lists.newArrayList();
    List<ITestNGMethod> afterXmlTestMethods = Lists.newArrayList();

    ClassInfoMap classMap = new ClassInfoMap(m_testClassesFromXml);
    m_testClassFinder= new TestNGClassFinder(classMap,Maps.<Class<?>, List<Object>>newHashMap(),
                                             m_configuration, this, m_dataProviderListeners);
    ITestMethodFinder testMethodFinder = new TestNGMethodFinder(m_runInfo, m_annotationFinder, comparator);

    m_runInfo.setTestMethods(testMethods);

    //
    // Initialize TestClasses
    //
    IClass[] classes = m_testClassFinder.findTestClasses();

    for (IClass ic : classes) {

      // Create TestClass
      ITestClass tc = new TestClass(ic,
                                   testMethodFinder,
                                   m_annotationFinder,
                                   m_runInfo,
                                   m_xmlTest,
                                   classMap.getXmlClass(ic.getRealClass()));
      m_classMap.put(ic.getRealClass(), tc);
    }

    //
    // Calculate groups methods
    //
    Map<String, List<ITestNGMethod>> beforeGroupMethods =
        MethodGroupsHelper.findGroupsMethods(m_classMap.values(), true);
    Map<String, List<ITestNGMethod>> afterGroupMethods =
        MethodGroupsHelper.findGroupsMethods(m_classMap.values(), false);

    //
    // Walk through all the TestClasses, store their method
    // and initialize them with the correct ITestClass
    //
    for (ITestClass tc : m_classMap.values()) {
      fixMethodsWithClass(tc.getTestMethods(), tc, testMethods);
      fixMethodsWithClass(tc.getBeforeClassMethods(), tc, beforeClassMethods);
      fixMethodsWithClass(tc.getBeforeTestMethods(), tc, null);
      fixMethodsWithClass(tc.getAfterTestMethods(), tc, null);
      fixMethodsWithClass(tc.getAfterClassMethods(), tc, afterClassMethods);
      fixMethodsWithClass(tc.getBeforeSuiteMethods(), tc, beforeSuiteMethods);
      fixMethodsWithClass(tc.getAfterSuiteMethods(), tc, afterSuiteMethods);
      fixMethodsWithClass(tc.getBeforeTestConfigurationMethods(), tc, beforeXmlTestMethods);
      fixMethodsWithClass(tc.getAfterTestConfigurationMethods(), tc, afterXmlTestMethods);
      fixMethodsWithClass(tc.getBeforeGroupsMethods(), tc,
          MethodHelper.uniqueMethodList(beforeGroupMethods.values()));
      fixMethodsWithClass(tc.getAfterGroupsMethods(), tc,
          MethodHelper.uniqueMethodList(afterGroupMethods.values()));
    }

    //
    // Sort the methods
    //
    m_beforeSuiteMethods = MethodHelper.collectAndOrderMethods(beforeSuiteMethods,
                                                              false /* forTests */,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              true /* unique */,
                                                              m_excludedMethods, comparator);

    m_beforeXmlTestMethods = MethodHelper.collectAndOrderMethods(beforeXmlTestMethods,
                                                              false /* forTests */,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              true /* unique (CQ added by me)*/,
                                                              m_excludedMethods, comparator);

    m_allTestMethods = MethodHelper.collectAndOrderMethods(testMethods,
                                                                true /* forTest? */,
                                                                m_runInfo,
                                                                m_annotationFinder,
                                                                false /* unique */,
                                                                m_excludedMethods, comparator);
    m_classMethodMap = new ClassMethodMap(testMethods, m_xmlMethodSelector);

    m_afterXmlTestMethods = MethodHelper.collectAndOrderMethods(afterXmlTestMethods,
                                                              false /* forTests */,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              true /* unique (CQ added by me)*/,
                                                              m_excludedMethods, comparator);

    m_afterSuiteMethods = MethodHelper.collectAndOrderMethods(afterSuiteMethods,
                                                              false /* forTests */,
                                                              m_runInfo,
                                                              m_annotationFinder,
                                                              true /* unique */,
                                                              m_excludedMethods, comparator);
    // shared group methods
    m_groupMethods = new ConfigurationGroupMethods(m_allTestMethods, beforeGroupMethods, afterGroupMethods);


  }

  public Collection<ITestClass> getTestClasses() {
    return m_classMap.values();
  }

  public void setTestName(String name) {
    m_testName = name;
  }

  public void setOutputDirectory(String od) {
    m_outputDirectory= od;
  }

  private void addMetaGroup(String name, List<String> groupNames) {
    m_metaGroups.put(name, groupNames);
  }

  private Map<String, String> createGroups(List<String> groups) {
    return GroupsHelper.createGroups(m_metaGroups, groups);
  }

  /**
   * The main entry method for TestRunner.
   *
   * This is where all the hard work is done:
   * - Invoke configuration methods
   * - Invoke test methods
   * - Catch exceptions
   * - Collect results
   * - Invoke listeners
   * - etc...
   */
  public void run() {
    beforeRun();

    try {
      XmlTest test= getTest();
      if(test.isJUnit()) {
        privateRunJUnit();
      }
      else {
        privateRun(test);
      }
    }
    finally {
      afterRun();
    }
  }

  /** Before run preparements. */
  private void beforeRun() {
    //
    // Log the start date
    //
    m_startDate = new Date(System.currentTimeMillis());

    // Log start
    logStart();

    // Invoke listeners
    fireEvent(true /*start*/);

    // invoke @BeforeTest
    ITestNGMethod[] testConfigurationMethods= getBeforeTestConfigurationMethods();
    if(null != testConfigurationMethods && testConfigurationMethods.length > 0) {
      m_invoker.invokeConfigurations(null,
                                     testConfigurationMethods,
                                     m_xmlTest.getSuite(),
                                     m_xmlTest.getAllParameters(),
                                     null, /* no parameter values */
                                     null /* instance */);
    }
  }

  private void privateRunJUnit() {
    final ClassInfoMap cim = new ClassInfoMap(m_testClassesFromXml, false);
    final Set<Class<?>> classes = cim.getClasses();
    final List<ITestNGMethod> runMethods = Lists.newArrayList();
    List<IWorker<ITestNGMethod>> workers = Lists.newArrayList();
    // FIXME: directly referencing JUnitTestRunner which uses JUnit classes
    // may result in an class resolution exception under different JVMs
    // The resolution process is not specified in the JVM spec with a specific implementation,
    // so it can be eager => failure
    workers.add(new IWorker<ITestNGMethod>() {
      /**
       * @see TestMethodWorker#getTimeOut()
       */
      @Override
      public long getTimeOut() {
        return 0;
      }

      /**
       * @see java.lang.Runnable#run()
       */
      @Override
      public void run() {
        for(Class<?> tc: classes) {
          List<XmlInclude> includedMethods = cim.getXmlClass(tc).getIncludedMethods();
          List<String> methods = Lists.newArrayList();
          for (XmlInclude inc: includedMethods) {
              methods.add(inc.getName());
          }
          IJUnitTestRunner tr= ClassHelper.createTestRunner(TestRunner.this);
          tr.setInvokedMethodListeners(m_invokedMethodListeners);
          try {
            tr.run(tc, methods.toArray(new String[methods.size()]));
          }
          catch(Exception ex) {
            ex.printStackTrace();
          }
          finally {
            runMethods.addAll(tr.getTestMethods());
          }
        }
      }

      @Override
      public List<ITestNGMethod> getTasks() {
        throw new TestNGException("JUnit not supported");
      }

      @Override
      public int getPriority() {
        if (m_allTestMethods.length == 1) {
          return m_allTestMethods[0].getPriority();
        } else {
          return 0;
        }
      }

      @Override
      public int compareTo(IWorker<ITestNGMethod> other) {
        return getPriority() - other.getPriority();
      }
    });

    runJUnitWorkers(workers);
    m_allTestMethods= runMethods.toArray(new ITestNGMethod[runMethods.size()]);
  }

  /**
   * Main method that create a graph of methods and then pass it to the
   * graph executor to run them.
   */
  private void privateRun(XmlTest xmlTest) {
    boolean parallel = xmlTest.getParallel().isParallel();

    {
      // parallel
      int threadCount = parallel ? xmlTest.getThreadCount() : 1;
      // Make sure we create a graph based on the intercepted methods, otherwise an interceptor
      // removing methods would cause the graph never to terminate (because it would expect
      // termination from methods that never get invoked).
      DynamicGraph<ITestNGMethod> graph = DynamicGraphHelper.createDynamicGraph(intercept(m_allTestMethods),
              getCurrentXmlTest());
      if (parallel) {
        if (graph.getNodeCount() > 0) {
          GraphThreadPoolExecutor<ITestNGMethod> executor =
                  new GraphThreadPoolExecutor<>("test=" + xmlTest.getName(), graph, this,
                          threadCount, threadCount, 0, TimeUnit.MILLISECONDS,
                          new LinkedBlockingQueue<Runnable>());
          executor.run();
          try {
            long timeOut = m_xmlTest.getTimeOut(XmlTest.DEFAULT_TIMEOUT_MS);
            Utils.log("TestRunner", 2, "Starting executor for test " + m_xmlTest.getName()
                + " with time out:" + timeOut + " milliseconds.");
            executor.awaitTermination(timeOut, TimeUnit.MILLISECONDS);
            executor.shutdownNow();
          } catch (InterruptedException handled) {
            handled.printStackTrace();
            Thread.currentThread().interrupt();
          }
        }
      } else {
        List<ITestNGMethod> freeNodes = graph.getFreeNodes();

        if (graph.getNodeCount() > 0 && freeNodes.isEmpty()) {
          throw new TestNGException("No free nodes found in:" + graph);
        }

        while (! freeNodes.isEmpty()) {
          List<IWorker<ITestNGMethod>> runnables = createWorkers(freeNodes);
          for (IWorker<ITestNGMethod> r : runnables) {
            r.run();
          }
          graph.setStatus(freeNodes, Status.FINISHED);
          freeNodes = graph.getFreeNodes();
        }
      }
    }
  }

  /**
   * Apply the method interceptor (if applicable) to the list of methods.
   */
  private ITestNGMethod[] intercept(ITestNGMethod[] methods) {

    List<IMethodInstance> methodInstances = MethodHelper.methodsToMethodInstances(Arrays.asList(methods));

    // add built-in interceptor (PreserveOrderMethodInterceptor or InstanceOrderingMethodInterceptor at the end of the list
    m_methodInterceptors.add(builtinInterceptor);
    for (IMethodInterceptor m_methodInterceptor : m_methodInterceptors) {
      methodInstances = m_methodInterceptor.intercept(methodInstances, this);
    }

    List<ITestNGMethod> result = MethodHelper.methodInstancesToMethods(methodInstances);

    //Since an interceptor is involved, we would need to ensure that the ClassMethodMap object is in sync with the
    //output of the interceptor, else @AfterClass doesn't get executed at all when interceptors are involved.
    //so let's update the current classMethodMap object with the list of methods obtained from the interceptor.
    this.m_classMethodMap = new ClassMethodMap(result, null);

    ITestNGMethod[] resultArray = result.toArray(new ITestNGMethod[result.size()]);

    //Check if an interceptor had altered the effective test method count. If yes, then we need to
    //update our configurationGroupMethod object with that information.
    if (resultArray.length != m_groupMethods.getAllTestMethods().length) {
      m_groupMethods = new ConfigurationGroupMethods(resultArray, m_groupMethods.getBeforeGroupsMethods(),
          m_groupMethods.getAfterGroupsMethods());
    }
    return resultArray;
  }

  /**
   * Create a list of workers to run the methods passed in parameter.
   * Each test method is run in its own worker except in the following cases:
   * - The method belongs to a class that has @Test(sequential=true)
   * - The parallel attribute is set to "classes"
   * In both these cases, all the methods belonging to that class will then
   * be put in the same worker in order to run in the same thread.
   */
  @Override
  public List<IWorker<ITestNGMethod>> createWorkers(List<ITestNGMethod> methods) {
    AbstractParallelWorker.Arguments args = new AbstractParallelWorker.Arguments.Builder()
            .classMethodMap(this.m_classMethodMap)
            .configMethods(this.m_groupMethods)
            .finder(this.m_annotationFinder)
            .invoker(this.m_invoker)
            .methods(methods)
            .testContext(this)
            .listeners(this.m_classListeners.values()).build();
    return AbstractParallelWorker.newWorker(m_xmlTest.getParallel()).createWorkers(args);
  }

  //
  // Invoke the workers
  //
  private void runJUnitWorkers(List<? extends IWorker<ITestNGMethod>> workers) {
      //
      // Sequential run
      //
      for (IWorker<ITestNGMethod> tmw : workers) {
        tmw.run();
      }
  }

  private void afterRun() {
    // invoke @AfterTest
    ITestNGMethod[] testConfigurationMethods= getAfterTestConfigurationMethods();
    if(null != testConfigurationMethods && testConfigurationMethods.length > 0) {
      m_invoker.invokeConfigurations(null,
                                     testConfigurationMethods,
                                     m_xmlTest.getSuite(),
                                     m_xmlTest.getAllParameters(),
                                     null, /* no parameter values */
                                     null /* instance */);
    }

    //
    // Log the end date
    //
    m_endDate = new Date(System.currentTimeMillis());

    dumpInvokedMethods();

    // Invoke listeners
    fireEvent(false /*stop*/);
  }

  /**
   * Logs the beginning of the {@link #beforeRun()} .
   */
  private void logStart() {
    log(3,
        "Running test " + m_testName + " on " + m_classMap.size() + " " + " classes, "
        + " included groups:[" + Strings.valueOf(m_xmlMethodSelector.getIncludedGroups())
        + "] excluded groups:[" + Strings.valueOf(m_xmlMethodSelector.getExcludedGroups()) + "]");

    if (getVerbose() >= 3) {
      for (ITestClass tc : m_classMap.values()) {
        ((TestClass) tc).dump();
      }
    }
  }

  /**
   * Trigger the start/finish event.
   *
   * @param isStart <tt>true</tt> if the event is for start, <tt>false</tt> if the
   *                event is for finish
   */
  private void fireEvent(boolean isStart) {
    for (ITestListener itl : m_testListeners) {
      if (isStart) {
        itl.onStart(this);
      }
      else {
        itl.onFinish(this);
      }
    }
  }

  /////
  // ITestContext
  //
  @Override
  public String getName() {
    return m_testName;
  }

  /**
   * @return Returns the startDate.
   */
  @Override
  public Date getStartDate() {
    return m_startDate;
  }

  /**
   * @return Returns the endDate.
   */
  @Override
  public Date getEndDate() {
    return m_endDate;
  }

  @Override
  public IResultMap getPassedTests() {
    return m_passedTests;
  }

  @Override
  public IResultMap getSkippedTests() {
    return m_skippedTests;
  }

  @Override
  public IResultMap getFailedTests() {
    return m_failedTests;
  }

  @Override
  public IResultMap getFailedButWithinSuccessPercentageTests() {
    return m_failedButWithinSuccessPercentageTests;
  }

  @Override
  public String[] getIncludedGroups() {
    Map<String, String> ig= m_xmlMethodSelector.getIncludedGroups();
    return ig.values().toArray(new String[ig.size()]);
  }

  @Override
  public String[] getExcludedGroups() {
    Map<String, String> eg= m_xmlMethodSelector.getExcludedGroups();
    return eg.values().toArray(new String[eg.size()]);
  }

  @Override
  public String getOutputDirectory() {
    return m_outputDirectory;
  }

  /**
   * @return Returns the suite.
   */
  @Override
  public ISuite getSuite() {
    return m_suite;
  }

  @Override
  public ITestNGMethod[] getAllTestMethods() {
    return m_allTestMethods;
  }

  @Override
  public String getHost() {
    return m_host;
  }

  @Override
  public Collection<ITestNGMethod> getExcludedMethods() {
    Map<ITestNGMethod, ITestNGMethod> vResult = Maps.newHashMap();

    for (ITestNGMethod m : m_excludedMethods) {
      vResult.put(m, m);
    }

    return vResult.keySet();
  }

  /**
   * @see org.testng.ITestContext#getFailedConfigurations()
   */
  @Override
  public IResultMap getFailedConfigurations() {
    return m_failedConfigurations;
  }

  /**
   * @see org.testng.ITestContext#getPassedConfigurations()
   */
  @Override
  public IResultMap getPassedConfigurations() {
    return m_passedConfigurations;
  }

  /**
   * @see org.testng.ITestContext#getSkippedConfigurations()
   */
  @Override
  public IResultMap getSkippedConfigurations() {
    return m_skippedConfigurations;
  }

  //
  // ITestContext
  /////

  /////
  // ITestResultNotifier
  //

  @Override
  public void addPassedTest(ITestNGMethod tm, ITestResult tr) {
    m_passedTests.addResult(tr, tm);
  }

  @Override
  public Set<ITestResult> getPassedTests(ITestNGMethod tm) {
    return m_passedTests.getResults(tm);
  }

  @Override
  public Set<ITestResult> getFailedTests(ITestNGMethod tm) {
    return m_failedTests.getResults(tm);
  }

  @Override
  public Set<ITestResult> getSkippedTests(ITestNGMethod tm) {
    return m_skippedTests.getResults(tm);
  }

  @Override
  public void addSkippedTest(ITestNGMethod tm, ITestResult tr) {
    m_skippedTests.addResult(tr, tm);
  }

  @Override
  public void addInvokedMethod(InvokedMethod im) {
    m_invokedMethods.add(im);
  }

  @Override
  public void addFailedTest(ITestNGMethod testMethod, ITestResult result) {
    logFailedTest(testMethod, result, false /* withinSuccessPercentage */);
  }

  @Override
  public void addFailedButWithinSuccessPercentageTest(ITestNGMethod testMethod,
                                                      ITestResult result) {
    logFailedTest(testMethod, result, true /* withinSuccessPercentage */);
  }

  @Override
  public XmlTest getTest() {
    return m_xmlTest;
  }

  @Override
  public List<ITestListener> getTestListeners() {
    return m_testListeners;
  }

  @Override
  public List<IConfigurationListener> getConfigurationListeners() {
    return Lists.newArrayList(m_configurationListeners);
  }
  //
  // ITestResultNotifier
  /////

  private void logFailedTest(ITestNGMethod method,
                             ITestResult tr,
                             boolean withinSuccessPercentage) {

    if (withinSuccessPercentage) {
      m_failedButWithinSuccessPercentageTests.addResult(tr, method);
    }
    else {
      m_failedTests.addResult(tr, method);
    }
  }

  private static void log(int level, String s) {
    Utils.log("TestRunner", level, s);
  }

  public static int getVerbose() {
    return m_verbose;
  }

  public void setVerbose(int n) {
    m_verbose = n;
  }

  //TODO: This method needs to be removed and we need to be leveraging addListener().
  //Investigate and fix this.
  void addTestListener(ITestListener listener) {
    m_testListeners.add(listener);
  }

  public void addListener(ITestNGListener listener) {
    // TODO a listener may be added many times if it implements many interfaces
    if (listener instanceof IMethodInterceptor) {
      m_methodInterceptors.add((IMethodInterceptor) listener);
    }
    if (listener instanceof ITestListener) {
      // At this point, the field m_testListeners has already been used in the creation
      addTestListener((ITestListener) listener);
    }
    if (listener instanceof IClassListener) {
      IClassListener classListener = (IClassListener) listener;
      if (!m_classListeners.containsKey(classListener.getClass())) {
        m_classListeners.put(classListener.getClass(), classListener);
      }
    }
    if (listener instanceof IConfigurationListener) {
      addConfigurationListener((IConfigurationListener) listener);
    }
    if (listener instanceof IConfigurable) {
      m_configuration.setConfigurable((IConfigurable) listener);
    }
    if (listener instanceof IHookable) {
      m_configuration.setHookable((IHookable) listener);
    }
    if (listener instanceof IExecutionListener) {
      IExecutionListener iel = (IExecutionListener) listener;
      iel.onExecutionStart();
      m_configuration.addExecutionListener(iel);
    }
    if (listener instanceof IDataProviderListener) {
      IDataProviderListener dataProviderListener = (IDataProviderListener) listener;
      m_dataProviderListeners.put(dataProviderListener.getClass(), dataProviderListener);
    }
    m_suite.addListener(listener);
  }

  void addConfigurationListener(IConfigurationListener icl) {
    m_configurationListeners.add(icl);
  }

  private final Collection<IInvokedMethod> m_invokedMethods = new ConcurrentLinkedQueue<>();

  private void dumpInvokedMethods() {
    MethodHelper.dumpInvokedMethodsInfoToConsole(m_invokedMethods, getVerbose());
  }

  public List<ITestNGMethod> getInvokedMethods() {
    return MethodHelper.invokedMethodsToMethods(m_invokedMethods);
  }

  private IResultMap m_passedConfigurations= new ResultMap();
  private IResultMap m_skippedConfigurations= new ResultMap();
  private IResultMap m_failedConfigurations= new ResultMap();

  private class ConfigurationListener implements IConfigurationListener2 {
    @Override
    public void beforeConfiguration(ITestResult tr) {
    }

    @Override
    public void onConfigurationFailure(ITestResult itr) {
      m_failedConfigurations.addResult(itr, itr.getMethod());
    }

    @Override
    public void onConfigurationSkip(ITestResult itr) {
      m_skippedConfigurations.addResult(itr, itr.getMethod());
    }

    @Override
    public void onConfigurationSuccess(ITestResult itr) {
      m_passedConfigurations.addResult(itr, itr.getMethod());
    }
  }

  void addMethodInterceptor(IMethodInterceptor methodInterceptor){
    m_methodInterceptors.add(methodInterceptor);
  }

  @Override
  public XmlTest getCurrentXmlTest() {
    return m_xmlTest;
  }

  private IAttributes m_attributes = new Attributes();

  @Override
  public Object getAttribute(String name) {
    return m_attributes.getAttribute(name);
  }

  @Override
  public void setAttribute(String name, Object value) {
    m_attributes.setAttribute(name, value);
  }

  @Override
  public Set<String> getAttributeNames() {
    return m_attributes.getAttributeNames();
  }

  @Override
  public Object removeAttribute(String name) {
    return m_attributes.removeAttribute(name);
  }

  private ListMultiMap<Class<? extends Module>, Module> m_guiceModules = Maps.newListMultiMap();

  @Override
  public List<Module> getGuiceModules(Class<? extends Module> cls) {
    return m_guiceModules.get(cls);
  }

  private Map<List<Module>, Injector> m_injectors = Maps.newHashMap();

  @Override
  public Injector getInjector(List<Module> moduleInstances) {
    return m_injectors .get(moduleInstances);
  }

  @Override
  public Injector getInjector(IClass iClass) {
    return guiceHelper.getInjector(iClass);
  }

  @Override
  public void addInjector(List<Module> moduleInstances, Injector injector) {
    m_injectors.put(moduleInstances, injector);
  }

} // TestRunner
