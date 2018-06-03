package junit.com.superbiz.servlet;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.meecrowave.junit.MeecrowaveRule;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;

import java.io.IOException;


public class HelloWorldServletTest {

  @Rule // started once for the class, @Rule would be per method
  public final MeecrowaveRule rule = new MeecrowaveRule();

  @Test
  public void test001() throws IOException {

    OkHttpClient client = new OkHttpClient();

    Request request = new Request.Builder()
        .url("http://127.0.0.1:" + rule.getConfiguration().getHttpPort() + "/?value=HalloNase")
        .get()
        .build();
    Response response = client.newCall(request).execute();
    String   string   = response.body().string().trim();
    System.out.println("string = " + string);
    Assert.assertEquals("HALLONASE", string);
  }
}
