/*
 * Copyright © 2016 David Sargent
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software
 * and associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation  the rights to use, copy, modify, merge, publish, distribute, sublicense,
 * and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING
 * BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING
 * FROM,OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package org.ftccommunity.ftcxtensible.robot;

import com.qualcomm.robotcore.hardware.DcMotorController;

import org.ftccommunity.ftcxtensible.interfaces.OpModeLoop;
import org.ftccommunity.ftcxtensible.interfaces.RunAssistant;
import org.ftccommunity.ftcxtensible.internal.Alpha;
import org.ftccommunity.ftcxtensible.internal.NotDocumentedWell;

import java.util.LinkedList;
import java.util.List;
import java.util.Map;

@Alpha
@NotDocumentedWell
public abstract class ReadWriteExtensibleOpMode extends ExtensibleOpMode {
    // TODO: 1/4/2016 throw out this design, and start from scratch
    private final DcMotorController[] controllers;
    private boolean skipReadMode;
    private boolean isInReadyMode;
    private int runEveryXTimes;
    private int readOnlyPos;
    private int writeOnlyPos;

    public ReadWriteExtensibleOpMode(int howOften, final DcMotorController... ctrls) {
        if (howOften < 1) {
            throw new IllegalArgumentException("howOften is less than 1");
        }

        controllers = ctrls.clone();
        modifyLoopChangeoverNumber(howOften);
    }

    protected final void skipAnyReadModeChangeOver() {
        skipReadMode = true;
    }

    public final boolean isInReadMode() {
        return isInReadyMode;
    }

    protected final void changeLoopChangeoverNumber(int loopCount) {
        Map<Integer, LinkedList<RunAssistant>> everyX = loopManager().getRegisterAfterEveryX();

        if (everyX.get(loopCount).get(readOnlyPos) instanceof ReadOnlySwitch) {
            loopManager().unregisterAfterEveryX(loopCount, readOnlyPos);
        } else { // We need to find our registration
            loopManager().getPossibleCandidatesForAfterEveryX(loopCount, ReadOnlySwitch.class.getSimpleName());
        }

        if (everyX.get(loopCount + 1).get(writeOnlyPos) instanceof WriteOnlyAssistant) {
            loopManager().unregisterAfterEveryX(loopCount + 1, writeOnlyPos);
        } else { // We need to find our registration
            List<Integer> candidates = loopManager().getPossibleCandidatesForAfterEveryX(
                    loopCount, WriteOnlyAssistant.class.getSimpleName());
            for (Integer candidate : candidates) {
                if (everyX.get(loopCount).get(candidate) instanceof WriteOnlyAssistant) {
                    loopManager().unregisterAfterEveryX(loopCount, candidate);
                }
            }
        }

        modifyLoopChangeoverNumber(loopCount);
    }

    private void modifyLoopChangeoverNumber(int loopCount) {
        int currentStatus = loopManager().getRegisterAfterEveryX().size();
        writeOnlyPos = currentStatus;
        readOnlyPos = ++currentStatus;

        loopManager().registerAfterEveryX(loopCount, new ReadOnlySwitch());
        loopManager().registerNewLoopOnEveryX(loopCount, new ReadLoop());
        loopManager().registerAfterEveryX(loopCount + 3, new WriteOnlyAssistant());

        runEveryXTimes = loopCount;
    }

    protected abstract void readLoop(final RobotContext ctx, final LinkedList<Object> out);

    protected final int getLoopChangeoverNumber() {
        return runEveryXTimes;
    }

    private class ReadLoop implements OpModeLoop {
        @Override
        public void loop(RobotContext ctx, LinkedList<Object> out) throws Exception {
            if (!skipReadMode) {
                readLoop(ctx, out);
            } else {
                loop(ctx, out);
            }
        }
    }

    private class ReadOnlySwitch implements RunAssistant {
        @Override
        public void onExecute(RobotContext ctx, LinkedList<Object> out) throws Exception {
            if (!skipReadMode) {
                for (DcMotorController controller : controllers) {
                    controller.setMotorControllerDeviceMode(DcMotorController.DeviceMode.READ_ONLY);
                    out.add(controller);
                    isInReadyMode = true;
                    skipNextLoop();
                }
            }
        }
    }

    private class WriteOnlyAssistant implements RunAssistant {
        @Override
        public void onExecute(RobotContext ctx, LinkedList<Object> out) throws Exception {
            if (!skipReadMode) {
                for (DcMotorController controller : controllers) {
                    controller.setMotorControllerDeviceMode(DcMotorController.DeviceMode.WRITE_ONLY);
                    out.add(controller);
                    isInReadyMode = false;
                    skipNextLoop();
                }
            }
        }
    }
}
