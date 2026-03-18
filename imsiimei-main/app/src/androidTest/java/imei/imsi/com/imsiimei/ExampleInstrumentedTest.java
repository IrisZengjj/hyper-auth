package imei.imsi.com.imsiimei;

import android.content.Context;
import androidx.test.platform.app.InstrumentationRegistry;  // 更新了这个导入

import androidx.test.ext.junit.runners.AndroidJUnit4;  // 更新了这个导入

import org.junit.Test;
import org.junit.runner.RunWith;

import static org.junit.Assert.*;

/**
 * Instrumentation test, which will execute on an Android device.
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
@RunWith(AndroidJUnit4.class)
public class ExampleInstrumentedTest {
    @Test
    public void useAppContext() throws Exception {
        // Context of the app under test.
        Context appContext = InstrumentationRegistry.getInstrumentation().getTargetContext();  // 更新了这个调用

        assertEquals("imei.imsi.com.imsiimei", appContext.getPackageName());
    }
}
