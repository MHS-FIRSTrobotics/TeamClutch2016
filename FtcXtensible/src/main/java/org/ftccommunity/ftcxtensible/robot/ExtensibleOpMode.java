/*
 * Copyright © 2015 David Sargent
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 *  and/or sell copies of the Software, and  to permit persons to whom the Software is furnished to
 *  do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 *  BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 *  NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 *  DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.ftccommunity.ftcxtensible.robot;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Environment;
import android.util.Log;
import android.view.View;

import com.google.common.base.Throwables;
import com.google.common.collect.EvictingQueue;
import com.google.common.collect.ImmutableList;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.qualcomm.robotcore.eventloop.opmode.OpMode;
import com.qualcomm.robotcore.hardware.Gamepad;
import com.qualcomm.robotcore.hardware.HardwareMap;
import com.qualcomm.robotcore.robocol.Telemetry;
import com.qualcomm.robotcore.util.RobotLog;

import org.ftccommunity.bindings.DataBinder;
import org.ftccommunity.ftcxtensible.hardware.camera.ExtensibleCameraManager;
import org.ftccommunity.ftcxtensible.interfaces.AbstractRobotContext;
import org.ftccommunity.ftcxtensible.interfaces.FullOpMode;
import org.ftccommunity.ftcxtensible.interfaces.RunAssistant;
import org.ftccommunity.ftcxtensible.internal.Alpha;
import org.ftccommunity.ftcxtensible.internal.NotDocumentedWell;
import org.ftccommunity.ftcxtensible.networking.ServerSettings;
import org.ftccommunity.ftcxtensible.robot.handlers.RobotUncaughtExceptionHandler;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Collection;
import java.util.LinkedList;

import io.netty.handler.codec.http.multipart.InterfaceHttpData;

/**
 * The main Xtenisble OpMode, any devirable OpMode that this library presents has at one point devired from
 * this class. This bootstraps the majority of the OpMode processing done by the Xtensible library
 *
 * @author David Sargent - FTC5395
 * @since 0.1
 */
@Alpha
@NotDocumentedWell
public abstract class ExtensibleOpMode extends OpMode implements FullOpMode, AbstractRobotContext {
    public static final String TAG = "XTENSTIBLE_OP_MODE::";
    private transient static ExtensibleOpMode parent;

    private final Gamepad gamepad1;
    private final Gamepad gamepad2;
    private final HardwareMap hardwareMap;
    private final Telemetry telemetry;

    private ExtensibleLoopManager loopManager;

    private RobotContext robotContext;
    private int loopCount;
    private volatile int skipNextLoop;

    private boolean logTimes;
    private EvictingQueue<Integer> loopTimes;
    private VariableTracer tracer;

    /**
     * Bootstraps the Extensible OpMode to the Xtensible library
     */
    protected ExtensibleOpMode() {
        this.gamepad1 = super.gamepad1;
        this.gamepad2 = super.gamepad2;
        this.hardwareMap = super.hardwareMap;
        if (super.hardwareMap.appContext == null) {
            RobotLog.w("App Context is null during construction.");
        }

        this.telemetry = super.telemetry;
        loopCount = 0;
        skipNextLoop = 0;

        if (parent == null) {
            robotContext = new RobotContext(gamepad1, gamepad2, hardwareMap, telemetry);
            parent = this;
        } else {
            robotContext = parent.robotContext;
        }

        loopManager = new ExtensibleLoopManager(this);
        loopTimes = EvictingQueue.create(50);
    }

    /**
     * Creates a new Extensible OpMode, with the master being the given parent
     *
     * @param prt the parent Extensible OpMode
     */
    protected ExtensibleOpMode(ExtensibleOpMode prt) {
        this();
        parent = prt;
    }

    /**
     * Initialize the Extensible OpMode and perform the user code operations to initialize
     * the robot
     */
    @Override
    public final void init() {
        robotContext.prepare(super.hardwareMap.appContext);

        // Upgrade thread priority
        Thread.currentThread().setPriority(7);

        // Build an exception handler
        robotContext.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                Activity controller = (Activity) robotContext.appContext();
                @SuppressWarnings("ResourceType") PendingIntent intent = PendingIntent.getActivity(controller.getBaseContext(), 0,
                        new Intent(controller.getIntent()), controller.getIntent().getFlags());
                Thread.currentThread().setUncaughtExceptionHandler(new RobotUncaughtExceptionHandler(robotContext.appContext(), intent));
            }
        });

        LinkedList<Object> list = new LinkedList<>();
        try {
            init(robotContext, list);
        } catch (InterruptedException ex) {
            return;
        } catch (Exception e) {
            handleException(list, e);
        }

        postProcess(list, RobotStatus.MainStates.START);
        telemetry().sendData();
    }


    /**
     * Starts the user code operations to start the robot
     */
    @Override
    public final void start() {
        robotContext.status().setMainState(RobotStatus.MainStates.START);
        LinkedList<Object> list = new LinkedList<>();
        try {
            start(robotContext, list);
        } catch (InterruptedException ex) {
            return;
        } catch (Exception e) {
            handleException(list, e);
        }

        if (list.isEmpty()) {
            onSuccess(robotContext, robotContext.status().getMainRobotState(), null);
        } else {
            for (Object o : list) {
                onSuccess(robotContext, robotContext.status().getMainRobotState(), o);
            }
        }

        telemetry().sendData();
    }


    /**
     * The looping system of Extensible OpMode
     */
    @Override
    public final void loop() {
        loopCount++;
        if (skipNextLoop > 0) {
            skipNextLoop--;
            Log.i(TAG, "Skipping Loop #" + getLoopCount());
            return;
        }

        if (robotContext.status().getMainRobotState() == RobotStatus.MainStates.EXCEPTION &&
                (robotContext.status().getCurrentStateType() == RobotStatus.Type.FAILURE ||
                        robotContext.status().getCurrentStateType() == RobotStatus.Type.IDK)) {
            throw new IllegalStateException("Robot cannot continue to execute, due to an exception");
        }

        // Good to continue
        long startTime = System.nanoTime();

        // Update the gamepads
        gamepad1().updateGamepad(robotContext, super.gamepad1);
        gamepad2().updateGamepad(robotContext, super.gamepad2);

        // Pre loop init
        robotContext.status().setMainState(RobotStatus.MainStates.EXEC);
        LinkedList<Object> list = new LinkedList<>();

        // Start loop checks
        for (RunAssistant assistant : loopManager.getPreloopAssistants(getLoopCount())) {
            runAssistant(assistant);
        }

        // Main loop
        try {
            loop(robotContext, list);
        } catch (InterruptedException ex) {
            return;
        } catch (Exception e) {
            handleException(list, e);
        }

        // Post loop processing
        for (RunAssistant assistant : loopManager.getPostLoopAssistants(getLoopCount())) {
            runAssistant(assistant);
        }

        postProcess(list, robotContext.status().getMainRobotState());

        // Get the delta time and check if it was longer than 50ms
        long endTime = System.nanoTime();
        long timeTaken = (endTime - startTime);
        if (timeTaken - (1000000 * 50) > 0) {
            Log.w(TAG, "User code took long than " + 50 + "ms. Time: " +
                    timeTaken + "ms");
        }

        if (logTimes) {
            loopTimes.add((int) timeTaken);
            tracer.log();
        }
    }

    /**
     * Stop the robot
     */
    @Override
    public final void stop() {
        LinkedList<Object> list = new LinkedList<>();
        try {
            stop(robotContext, list);
        } catch (InterruptedException ex) {
            return;
        } catch (Exception e) {
            handleException(list, e);
        }

        postProcess(list, RobotStatus.MainStates.STOP);

        if (logTimes) {
            File perfFile = new File(Environment.getExternalStorageDirectory() + "/perf_" + System.currentTimeMillis() + ".json");
            Log.i(TAG, "Saving Loop Performance File at " + perfFile);
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            try {
                FileWriter outputStream = new FileWriter(perfFile);
                outputStream.write(gson.toJson(new PerformanceTuner((Integer[]) loopTimes.toArray())));
            } catch (IOException ex) {
                Log.e(TAG, ex.getMessage(), ex);
            }
        }

        parent = null;
        robotContext.release();
        robotContext = null;
    }

    /**
     * Post process the given details of the user code segment
     *
     * @param list
     * @param state
     */
    private void postProcess(LinkedList<Object> list, RobotStatus.MainStates state) {
        if (list.isEmpty()) {
            onSuccess(robotContext, state, null);
        } else {
            for (Object o : list) {
                onSuccess(robotContext, state, o);
            }
        }

        telemetry().sendData();
    }

    /**
     * Gets how many loop cycles have completed, including the current cycle
     *
     * @return the number of complete loop cycles, including the current one
     */
    protected final int getLoopCount() {
        return loopCount;
    }

    /**
     * Tells the loop manager to skip the next loop cycle, this is additive, so calling this function
     * twice skips two loops
     */
    protected final void skipNextLoop() {
        skipNextLoop++;
    }

    /**
     * Enables the current OpMode that devires this to be recorded for debugging reasons
     *
     * @param child the OpMode that needs to be monitored
     * @param <T>   the type of the monitored child
     */
    protected final <T> void enableLoopPerformanceCapture(@NotNull T child) {
        this.tracer = new VariableTracer<>(child);
        logTimes = true;
    }

    /**
     * Disables loop capture
     */
    protected final void disableLoopPerformanceCapture() {
        logTimes = false;
    }

    /**
     * Handles an user code exception
     *
     * @param list
     * @param e
     */
    private void handleException(LinkedList<Object> list, Exception e) {
        Log.e(TAG,
                "An exception occurred running the OpMode " + getCallerClassName(e), e);

        robotContext.status().setCurrentStateType(RobotStatus.Type.IDK);
        RobotStatus.Type failure = robotContext.status().getCurrentStateType();
        if (onFailure(robotContext, failure, RobotStatus.MainStates.EXCEPTION, e) >= 0) {
            robotContext.status().setCurrentStateType(RobotStatus.Type.SUCCESS);
            for (Object o : list) {
                onFailure(robotContext, failure, RobotStatus.MainStates.EXCEPTION, o);
            }
        } else {
            robotContext.status().setCurrentStateType(RobotStatus.Type.FAILURE);
            RobotLog.setGlobalErrorMsg(e.toString());
            Throwables.propagate(e);
        }
    }

    private String getCallerClassName(Exception e) {
        StackTraceElement[] stElements = e.getStackTrace();
        for (StackTraceElement ste : stElements) {
            if (!ste.getClassName().equals(ExtensibleOpMode.class.getName()) &&
                    ste.getClassName().indexOf("java.lang.Thread") != 0) {
                return ste.getMethodName() + ":" + ste.getLineNumber() + "@" + ste.getClassName();
            }
        }

        return "";
    }

    protected final RobotContext getContext() {
        return robotContext;
    }

    @NotNull
    public ExtensibleLoopManager loopManager() {
        return loopManager;
    }

    public ExtensibleGamepad gamepad1() {
        return robotContext.gamepad1();
    }

    public ExtensibleGamepad gamepad2() {
        return robotContext.gamepad2();
    }

    @Override
    public ExtensibleCameraManager cameraManager() {
        return robotContext.cameraManager();
    }

    @Override
    public void release() {
        robotContext.release();
    }

    @NotNull
    @Override
    public DataBinder controllerBindings() {
        return robotContext.controllerBindings();
    }

    @NotNull
    @Override
    public View robotControllerView() {
        return robotContext.robotControllerView();
    }

    @Override
    public RobotContext enableNetworking() {
        return robotContext.enableNetworking();
    }

    private void runAssistant(RunAssistant assistant) {
        LinkedList<Object> list = new LinkedList<>();
        try {
            assistant.onExecute(robotContext, list);
        } catch (InterruptedException ex) {
            handleInterrupt(ex);
            return;
        } catch (Exception e) {
            handleException(list, e);
        }

        postProcess(list, robotContext.status().getMainRobotState());
    }

    private void handleInterrupt(InterruptedException ex) {
        if (!Thread.currentThread().isInterrupted()) {
            Thread.currentThread().interrupt();
        }
        Throwables.propagate(ex);
    }

    @Override
    public RobotContext disableNetworking() {
        return robotContext.disableNetworking();
    }

    @Override
    public RobotContext startNetworking() {
        return robotContext.startNetworking();
    }

    @Override
    public RobotContext stopNetworking() {
        return robotContext.stopNetworking();
    }

    public ExtensibleHardwareMap hardwareMap() {
        return robotContext.hardwareMap();
    }

    @Override
    public void bindAppContext(Context context) throws IllegalArgumentException, IllegalStateException {
        robotContext.bindAppContext(context);
    }

    @Override
    public void prepare(Context ctx) {
        robotContext.prepare(ctx);
    }

    @Override
    public Context appContext() {
        return robotContext.appContext();
    }

    @Deprecated
    @Override
    public Gamepad legacyGamepad1() {
        return robotContext.legacyGamepad1();
    }

    @Deprecated
    @Override
    public Gamepad legacyGamepad2() {
        return robotContext.legacyGamepad2();
    }

    @Override
    public ServerSettings serverSettings() {
        return robotContext.serverSettings();
    }

    @Override
    public RobotLogger log() {
        return robotContext.log();
    }

    @Override
    public void submitAsyncTask(Runnable runnable) {
        robotContext.submitAsyncTask(runnable);
    }

    @Override
    public void runOnUiThread(Runnable runnable) {
        robotContext.runOnUiThread(runnable);
    }

    @Override
    public RobotStatus status() {
        return robotContext.status();
    }

    @Override
    public void addPostData(Collection<InterfaceHttpData> datas) {
        robotContext.addPostData(datas);
    }

    @Override
    public ImmutableList<InterfaceHttpData> getPostedData() {
        return robotContext.getPostedData();
    }

    public ExtensibleTelemetry telemetry() {
        return robotContext.telemetry();
    }

    private class VariableTrace {
        private String name;
        private Object value;

        public VariableTrace(String name, Object value) {
            this.name = name;
            this.value = value;
        }
    }

    class VariableTracer<T> {
        private transient T current;
        private LinkedList<VariableTrace> traces;

        public VariableTracer(T o) {
            current = o;
            traces = new LinkedList<>();
        }

        public void log() {
            LinkedList<VariableTrace> fields = new LinkedList<>();
            for (Field field : current.getClass().getDeclaredFields()) {
                if (!Modifier.isTransient(field.getModifiers())) {
                    try {
                        fields.add(new VariableTrace(field.getName(), field.get(current)));
                    } catch (IllegalAccessException | ClassCastException e) {
                        Log.e(TAG, e.getLocalizedMessage(), e);
                    }
                }
            }

            traces.addAll(fields);
        }

        public VariableTrace[] get() {
            return (VariableTrace[]) traces.toArray();
        }
    }
}
