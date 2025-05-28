package test.github1336;

import org.testng.annotations.Test;

public class TestNG2NoPriority extends BaseClass {
    @Test
    public void test1TestNG2() throws InterruptedException {
        runTest("http://testng.org/doc/download.html");
    }

    @Test
    public void test2TestNG2() throws InterruptedException {
        runTest("http://www3.lenovo.com/in/en/");
    }

    @Test
    public void test3TestNG2() throws InterruptedException {
        runTest("https://github.com/");
    }
}
