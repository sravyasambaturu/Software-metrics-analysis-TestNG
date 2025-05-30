package org.testng;

import com.beust.jcommander.Parameter;
import com.beust.jcommander.converters.CommaSeparatedConverter;

import org.testng.collections.Lists;

import java.util.ArrayList;
import java.util.List;

public class CommandLineArgs {

  @Parameter(description = "The XML suite files to run")
  public List<String> suiteFiles = Lists.newArrayList();

  @Parameter(names = { "-log", "-verbose" }, description = "Level of verbosity")
  public Integer verbose;

  @Parameter(names = "-groups", description = "Comma-separated list of group names to be run")
  public String groups;

  @Parameter(names = "-excludedgroups", description ="Comma-separated list of group names to "
      + " exclude")
  public String excludedGroups;
  
  @Parameter(names = "-d", description ="Output directory")
  public String outputDirectory;
  
  @Parameter(names = "-junit", description ="JUnit mode")
  public Boolean junit = Boolean.FALSE;

  @Parameter(names = "-listener", description = "List of .class files or list of class names" +
      " implementing ITestListener or ISuiteListener")
  public String listener;

  @Parameter(names = "-methodselectors", description = "List of .class files or list of class " +
  		"names implementing IMethodSelector")
  public String methodSelectors;

  @Parameter(names = "-objectfactory", description = "List of .class files or list of class " +
  		"names implementing ITestRunnerFactory")
  public String objectFactory;

  @Parameter(names = "-parallel", description = "Parallel mode (methods, tests or classes)")
  public String parallelMode;
  
  @Parameter(names = "-configfailurepolicy", description = "Configuration failure policy (skip or continue)")
  public String configFailurePolicy;

  @Parameter(names = "-threadcount", description = "Number of threads to use when running tests " +
      "in parallel")
  public Integer threadCount;

  @Parameter(names = "-dataproviderthreadcount", description = "Number of threads to use when " +
      "running data providers")
  public Integer dataProviderThreadCount;

  @Parameter(names = "-suitename", description = "Default name of test suite, if not specified " +
      "in suite definition file or source code")
  public String suiteName;

  @Parameter(names = "-testname", description = "Default name of test, if not specified in suite" +
      "definition file or source code")
  public String testName;

  @Parameter(names = "-reporter", description = "Extended configuration for custom report listener")
  public String reporter;

  /**
   * Used as map key for the complete list of report listeners provided with the above argument
   */
  @Parameter(names = "-reporterslist", hidden = true)
  public String reportersList;

  @Parameter(names = "-usedefaultlisteners", description = "Whether to use the default listeners")
  public String useDefaultListeners = "true";

  @Parameter(names = "-skipfailedinvocationcounts", hidden = true)
  public Boolean skipFailedInvocationCounts;

  @Parameter(names = "-testclass", description = "The list of test classes")
  public String testClass;

  @Parameter(names = "-testnames", description = "The list of test names to run")
  public String testNames;

  @Parameter(names = "-testjar", description = "A jar file containing the tests")
  public String testJar;

  @Parameter(names = "-testRunFactory", description = "The factory used to create tests")
  public String testRunFactory;

  @Parameter(names = "-port", description = "The port")
  public Integer port;

  @Parameter(names = "-host", description = "The host")
  public String host;

  @Parameter(names = "-master", description = "Host where the master is")
  public String master;

  @Parameter(names = "-slave", description = "Host where the slave is")
  public String slave;

  @Parameter(names = "-methods", description = "Comma separated of test methods",
      converter = CommaSeparatedConverter.class)
  public List<String> commandLineMethods = new ArrayList<String>();
}
