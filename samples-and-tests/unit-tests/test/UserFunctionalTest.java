import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Before;
import org.junit.Test;

import play.mvc.Http.Response;
import play.test.FunctionalTest;


public class UserFunctionalTest extends FunctionalTest {

    private String actualMd5;

    @Before
    public void setUp() throws IOException {
        actualMd5 = DigestUtils.md5Hex(new FileInputStream("test/googlelogo.png"));

    }

    @Test
    public void uploadAndGetBlob() throws IOException {
        Map<String,File> files = new HashMap<String, File>();
        files.put("image", new File("test/googlelogo.png"));
        Response response = POST("/image", Collections.<String, String>emptyMap(), files);

        assertIsOk(response);

        String id = getContent(response);

        Response imageResp = GET("/image/" + id);
        assertIsOk(imageResp);

        String responseMd5 = DigestUtils.md5Hex(imageResp.out.toByteArray());
        assertEquals(actualMd5, responseMd5);
    }
}
