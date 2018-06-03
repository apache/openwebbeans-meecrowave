package junit.com.superbiz.servlet;


import com.superbiz.servlet.UpperCaseService;
import org.junit.Assert;
import org.junit.Test;

public class UpperCaseServiceTest {

  @Test
  public void test001() {
    Assert.assertEquals("HALLO", new UpperCaseService().upperCase("hallo"));
  }
}
