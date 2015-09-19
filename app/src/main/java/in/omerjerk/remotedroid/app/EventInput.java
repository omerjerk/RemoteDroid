package in.omerjerk.remotedroid.app;

import android.hardware.input.InputManager;
import android.view.InputEvent;
import android.view.KeyEvent;
import android.view.MotionEvent;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Created by omerjerk on 19/9/15.
 *
 * Class to create seamless input/touch events on your Android device without root
 */
public class EventInput {

    Method injectInputEventMethod;
    InputManager im;

    public EventInput() throws Exception {
        //Get the instance of InputManager class using reflection
        String methodName = "getInstance";
        Object[] objArr = new Object[0];
        im = (InputManager) InputManager.class.getDeclaredMethod(methodName, new Class[0])
                .invoke(null, objArr);

        //Make MotionEvent.obtain() method accessible
        methodName = "obtain";
        MotionEvent.class.getDeclaredMethod(methodName, new Class[0]).setAccessible(true);

        //Get the reference to injectInputEvent method
        methodName = "injectInputEvent";
        injectInputEventMethod = InputManager.class.getMethod(
                methodName, new Class[]{InputEvent.class, Integer.TYPE});
    }

    public static void injectMotionEvent(InputManager im, Method injectInputEventMethod,
                                          int inputSource, int action, long when, float x, float y,
                                          float pressure) throws InvocationTargetException, IllegalAccessException {
        MotionEvent event = MotionEvent.obtain(when, when, action, x, y, pressure, 1.0f, 0, 1.0f, 1.0f, 0, 0);
        event.setSource(inputSource);
        injectInputEventMethod.invoke(im, new Object[]{event, Integer.valueOf(0)});
    }

    private static void injectKeyEvent(InputManager im, Method injectInputEventMethod, KeyEvent event)
            throws InvocationTargetException, IllegalAccessException {
        injectInputEventMethod.invoke(im, new Object[]{event, Integer.valueOf(0)});
    }
}
