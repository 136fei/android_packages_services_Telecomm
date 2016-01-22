/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.server.telecom;

import android.content.Context;
import android.media.AudioManager;
import android.os.Message;
import android.util.SparseArray;

import com.android.internal.util.State;
import com.android.internal.util.StateMachine;

public class CallAudioModeStateMachine extends StateMachine {
    public static class MessageArgs {
        public boolean hasActiveCalls;
        public boolean hasRingingCalls;
        public boolean hasHoldingCalls;
        public boolean isTonePlaying;
        public boolean foregroundCallIsVoip;
        public Session session;

        public MessageArgs(boolean hasActiveCalls, boolean hasRingingCalls, boolean hasHoldingCalls,
                boolean isTonePlaying, boolean foregroundCallIsVoip, Session session) {
            this.hasActiveCalls = hasActiveCalls;
            this.hasRingingCalls = hasRingingCalls;
            this.hasHoldingCalls = hasHoldingCalls;
            this.isTonePlaying = isTonePlaying;
            this.foregroundCallIsVoip = foregroundCallIsVoip;
            this.session = session;
        }

        @Override
        public String toString() {
            return "MessageArgs{" +
                    "hasActiveCalls=" + hasActiveCalls +
                    ", hasRingingCalls=" + hasRingingCalls +
                    ", hasHoldingCalls=" + hasHoldingCalls +
                    ", isTonePlaying=" + isTonePlaying +
                    ", foregroundCallIsVoip=" + foregroundCallIsVoip +
                    ", session=" + session +
                    '}';
        }
    }

    public static final int INITIALIZE = 1;
    // These ENTER_*_FOCUS commands are for testing.
    public static final int ENTER_CALL_FOCUS_FOR_TESTING = 2;
    public static final int ENTER_COMMS_FOCUS_FOR_TESTING = 3;
    public static final int ENTER_RING_FOCUS_FOR_TESTING = 4;
    public static final int ABANDON_FOCUS_FOR_TESTING = 5;

    public static final int NO_MORE_ACTIVE_OR_DIALING_CALLS = 1001;
    public static final int NO_MORE_RINGING_CALLS = 1002;
    public static final int NO_MORE_HOLDING_CALLS = 1003;

    public static final int NEW_ACTIVE_OR_DIALING_CALL = 2001;
    public static final int NEW_RINGING_CALL = 2002;
    public static final int NEW_HOLDING_CALL = 2003;
    public static final int MT_AUDIO_SPEEDUP_FOR_RINGING_CALL = 2004;

    public static final int TONE_STARTED_PLAYING = 3001;
    public static final int TONE_STOPPED_PLAYING = 3002;

    public static final int FOREGROUND_VOIP_MODE_CHANGE = 4001;

    private static final SparseArray<String> MESSAGE_CODE_TO_NAME = new SparseArray<String>() {{
        put(ENTER_CALL_FOCUS_FOR_TESTING, "ENTER_CALL_FOCUS_FOR_TESTING");
        put(ENTER_COMMS_FOCUS_FOR_TESTING, "ENTER_COMMS_FOCUS_FOR_TESTING");
        put(ENTER_RING_FOCUS_FOR_TESTING, "ENTER_RING_FOCUS_FOR_TESTING");
        put(ABANDON_FOCUS_FOR_TESTING, "ABANDON_FOCUS_FOR_TESTING");
        put(NO_MORE_ACTIVE_OR_DIALING_CALLS, "NO_MORE_ACTIVE_OR_DIALING_CALLS");
        put(NO_MORE_RINGING_CALLS, "NO_MORE_RINGING_CALLS");
        put(NO_MORE_HOLDING_CALLS, "NO_MORE_HOLDING_CALLS");
        put(NEW_ACTIVE_OR_DIALING_CALL, "NEW_ACTIVE_OR_DIALING_CALL");
        put(NEW_RINGING_CALL, "NEW_RINGING_CALL");
        put(NEW_HOLDING_CALL, "NEW_HOLDING_CALL");
        put(MT_AUDIO_SPEEDUP_FOR_RINGING_CALL, "MT_AUDIO_SPEEDUP_FOR_RINGING_CALL");
        put(TONE_STARTED_PLAYING, "TONE_STARTED_PLAYING");
        put(TONE_STOPPED_PLAYING, "TONE_STOPPED_PLAYING");
        put(FOREGROUND_VOIP_MODE_CHANGE, "FOREGROUND_VOIP_MODE_CHANGE");
    }};

    private class BaseState extends State {
        @Override
        public boolean processMessage(Message msg) {
            switch (msg.what) {
                case ENTER_CALL_FOCUS_FOR_TESTING:
                    transitionTo(mSimCallFocusState);
                    return HANDLED;
                case ENTER_COMMS_FOCUS_FOR_TESTING:
                    transitionTo(mVoipCallFocusState);
                    return HANDLED;
                case ENTER_RING_FOCUS_FOR_TESTING:
                    transitionTo(mRingingFocusState);
                    return HANDLED;
                case ABANDON_FOCUS_FOR_TESTING:
                    transitionTo(mUnfocusedState);
                    return HANDLED;
                case INITIALIZE:
                    mIsInitialized = true;
                    return HANDLED;
                default:
                    return NOT_HANDLED;
            }
        }
    }

    private class UnfocusedState extends BaseState {
        @Override
        public void enter() {
            if (mIsInitialized) {
                Log.i(LOG_TAG, "Abandoning audio focus: now UNFOCUSED");
                mAudioManager.abandonAudioFocusForCall();
                mAudioManager.setMode(AudioManager.MODE_NORMAL);

                mMostRecentMode = AudioManager.MODE_NORMAL;
                mCallAudioManager.setCallAudioRouteFocusState(CallAudioRouteStateMachine.NO_FOCUS);
            }
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            MessageArgs args = (MessageArgs) msg.obj;
            switch (msg.what) {
                case NO_MORE_ACTIVE_OR_DIALING_CALLS:
                    // Do nothing.
                    return HANDLED;
                case NO_MORE_RINGING_CALLS:
                    // Do nothing.
                    return HANDLED;
                case NO_MORE_HOLDING_CALLS:
                    // Do nothing.
                    return HANDLED;
                case NEW_ACTIVE_OR_DIALING_CALL:
                    Log.w(LOG_TAG, "Newly active/dialing call appeared from an unfocused state. " +
                            "Args are:\n" + args.toString());
                    transitionTo(args.foregroundCallIsVoip
                            ? mVoipCallFocusState : mSimCallFocusState);
                    return HANDLED;
                case NEW_RINGING_CALL:
                    transitionTo(mRingingFocusState);
                    return HANDLED;
                case NEW_HOLDING_CALL:
                    // This really shouldn't happen, but transition to the focused state anyway.
                    Log.w(LOG_TAG, "Call was surprisingly put into hold from an unknown state." +
                            " Args are: \n" + args.toString());
                    transitionTo(mOtherFocusState);
                    return HANDLED;
                case TONE_STARTED_PLAYING:
                    // This shouldn't happen either, but perform the action anyway.
                    Log.w(LOG_TAG, "Tone started playing unexpectedly. Args are: \n"
                            + args.toString());
                    return HANDLED;
                default:
                    // The forced focus switch commands are handled by BaseState.
                    return NOT_HANDLED;
            }
        }
    }

    private class RingingFocusState extends BaseState {
        @Override
        public void enter() {
            Log.i(LOG_TAG, "Audio focus entering RINGING state");
            mAudioManager.requestAudioFocusForCall(AudioManager.STREAM_RING,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            if (mMostRecentMode == AudioManager.MODE_IN_CALL) {
                // Preserving behavior from the old CallAudioManager.
                Log.i(LOG_TAG, "Transition from IN_CALL -> RINGTONE."
                        + "  Resetting to NORMAL first.");
                mAudioManager.setMode(AudioManager.MODE_NORMAL);
            }
            mAudioManager.setMode(AudioManager.MODE_RINGTONE);

            mCallAudioManager.startRinging();
            mCallAudioManager.setCallAudioRouteFocusState(CallAudioRouteStateMachine.HAS_FOCUS);
        }

        @Override
        public void exit() {
            // Audio mode and audio stream will be set by the next state.
            mCallAudioManager.stopRinging();
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            MessageArgs args = (MessageArgs) msg.obj;
            switch (msg.what) {
                case NO_MORE_ACTIVE_OR_DIALING_CALLS:
                    // Do nothing. Loss of an active call should not impact ringer.
                    return HANDLED;
                case NO_MORE_HOLDING_CALLS:
                    // Do nothing and keep ringing.
                    return HANDLED;
                case NO_MORE_RINGING_CALLS:
                    // If there are active or holding calls, switch to the appropriate focus.
                    // Otherwise abandon focus.
                    if (args.hasActiveCalls) {
                        if (args.foregroundCallIsVoip) {
                            transitionTo(mVoipCallFocusState);
                        } else {
                            transitionTo(mSimCallFocusState);
                        }
                    } else if (args.hasHoldingCalls || args.isTonePlaying) {
                        transitionTo(mOtherFocusState);
                    } else {
                        transitionTo(mUnfocusedState);
                    }
                    return HANDLED;
                case NEW_ACTIVE_OR_DIALING_CALL:
                    // If a call becomes active suddenly, give it priority over ringing.
                    transitionTo(args.foregroundCallIsVoip
                            ? mVoipCallFocusState : mSimCallFocusState);
                    return HANDLED;
                case NEW_RINGING_CALL:
                    Log.w(LOG_TAG, "Unexpected behavior! New ringing call appeared while in " +
                            "ringing state.");
                    return HANDLED;
                case NEW_HOLDING_CALL:
                    // This really shouldn't happen, but transition to the focused state anyway.
                    Log.w(LOG_TAG, "Call was surprisingly put into hold while ringing." +
                            " Args are: " + args.toString());
                    transitionTo(mOtherFocusState);
                    return HANDLED;
                case MT_AUDIO_SPEEDUP_FOR_RINGING_CALL:
                    // This happens when an IMS call is answered by the in-call UI. Special case
                    // that we have to deal with for some reason.

                    // VOIP calls should never invoke this mechanism, so transition directly to
                    // the sim call focus state.
                    transitionTo(mSimCallFocusState);
                    return HANDLED;
                default:
                    // The forced focus switch commands are handled by BaseState.
                    return NOT_HANDLED;
            }
        }
    }

    private class SimCallFocusState extends BaseState {
        @Override
        public void enter() {
            Log.i(LOG_TAG, "Audio focus entering SIM CALL state");
            mAudioManager.requestAudioFocusForCall(AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            mAudioManager.setMode(AudioManager.MODE_IN_CALL);
            mMostRecentMode = AudioManager.MODE_IN_CALL;
            mCallAudioManager.setCallAudioRouteFocusState(CallAudioRouteStateMachine.HAS_FOCUS);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            MessageArgs args = (MessageArgs) msg.obj;
            switch (msg.what) {
                case NO_MORE_ACTIVE_OR_DIALING_CALLS:
                    // Switch to either ringing, holding, or inactive
                    if (args.hasHoldingCalls || args.isTonePlaying) {
                        transitionTo(mOtherFocusState);
                    } else if (args.hasRingingCalls) {
                        transitionTo(mRingingFocusState);
                    } else {
                        transitionTo(mUnfocusedState);
                    }
                    return HANDLED;
                case NO_MORE_RINGING_CALLS:
                    // Don't transition state, but stop any call-waiting tones that may have been
                    // playing.
                    mCallAudioManager.stopCallWaiting();
                    return HANDLED;
                case NO_MORE_HOLDING_CALLS:
                    // Do nothing.
                    return HANDLED;
                case NEW_ACTIVE_OR_DIALING_CALL:
                    // Do nothing. Already active.
                    return HANDLED;
                case NEW_RINGING_CALL:
                    // Don't make a call ring over an active call, but do play a call waiting tone.
                    mCallAudioManager.startCallWaiting();
                    return HANDLED;
                case NEW_HOLDING_CALL:
                    // Don't do anything now. Putting an active call on hold will be handled when
                    // NO_MORE_ACTIVE_CALLS is processed.
                    return HANDLED;
                case FOREGROUND_VOIP_MODE_CHANGE:
                    if (args.foregroundCallIsVoip) {
                        transitionTo(mVoipCallFocusState);
                    }
                    return HANDLED;
                default:
                    // The forced focus switch commands are handled by BaseState.
                    return NOT_HANDLED;
            }
        }
    }

    private class VoipCallFocusState extends BaseState {
        @Override
        public void enter() {
            Log.i(LOG_TAG, "Audio focus entering VOIP CALL state");
            mAudioManager.requestAudioFocusForCall(AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            mAudioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            mMostRecentMode = AudioManager.MODE_IN_COMMUNICATION;
            mCallAudioManager.setCallAudioRouteFocusState(CallAudioRouteStateMachine.HAS_FOCUS);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            MessageArgs args = (MessageArgs) msg.obj;
            switch (msg.what) {
                case NO_MORE_ACTIVE_OR_DIALING_CALLS:
                    // Switch to either ringing, holding, or inactive
                    if (args.hasHoldingCalls || args.isTonePlaying) {
                        transitionTo(mOtherFocusState);
                    } else if (args.hasRingingCalls) {
                        transitionTo(mRingingFocusState);
                    } else {
                        transitionTo(mUnfocusedState);
                    }
                    return HANDLED;
                case NO_MORE_RINGING_CALLS:
                    // Don't transition state, but stop any call-waiting tones that may have been
                    // playing.
                    mCallAudioManager.stopCallWaiting();
                    return HANDLED;
                case NO_MORE_HOLDING_CALLS:
                    // Do nothing.
                    return HANDLED;
                case NEW_ACTIVE_OR_DIALING_CALL:
                    // Do nothing. Already active.
                    return HANDLED;
                case NEW_RINGING_CALL:
                    // Don't make a call ring over an active call, but do play a call waiting tone.
                    mCallAudioManager.startCallWaiting();
                    return HANDLED;
                case NEW_HOLDING_CALL:
                    // Don't do anything now. Putting an active call on hold will be handled when
                    // NO_MORE_ACTIVE_CALLS is processed.
                    return HANDLED;
                case FOREGROUND_VOIP_MODE_CHANGE:
                    if (!args.foregroundCallIsVoip) {
                        transitionTo(mSimCallFocusState);
                    }
                    return HANDLED;
                default:
                    // The forced focus switch commands are handled by BaseState.
                    return NOT_HANDLED;
            }
        }
    }

    /**
     * This class is used for calls on hold and end-of-call tones.
     */
    private class OtherFocusState extends BaseState {
        @Override
        public void enter() {
            Log.i(LOG_TAG, "Audio focus entering TONE/HOLDING state");
            mAudioManager.requestAudioFocusForCall(AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT);
            mAudioManager.setMode(mMostRecentMode);
            mCallAudioManager.setCallAudioRouteFocusState(CallAudioRouteStateMachine.HAS_FOCUS);
        }

        @Override
        public boolean processMessage(Message msg) {
            if (super.processMessage(msg) == HANDLED) {
                return HANDLED;
            }
            MessageArgs args = (MessageArgs) msg.obj;
            switch (msg.what) {
                case NO_MORE_HOLDING_CALLS:
                    if (args.hasActiveCalls) {
                        transitionTo(args.foregroundCallIsVoip
                                ? mVoipCallFocusState : mSimCallFocusState);
                    } else if (args.hasRingingCalls) {
                        transitionTo(mRingingFocusState);
                    } else if (!args.isTonePlaying) {
                        transitionTo(mUnfocusedState);
                    }
                    // Do nothing if a tone is playing.
                    return HANDLED;
                case NEW_ACTIVE_OR_DIALING_CALL:
                    transitionTo(args.foregroundCallIsVoip
                            ? mVoipCallFocusState : mSimCallFocusState);
                    return HANDLED;
                case NEW_RINGING_CALL:
                    // Apparently this is current behavior. Should this be the case?
                    transitionTo(mRingingFocusState);
                    return HANDLED;
                case NEW_HOLDING_CALL:
                    // Do nothing.
                    return HANDLED;
                case NO_MORE_RINGING_CALLS:
                    // If there are no more ringing calls in this state, then stop any call-waiting
                    // tones that may be playing.
                    mCallAudioManager.stopCallWaiting();
                    return HANDLED;
                case TONE_STOPPED_PLAYING:
                    if (!args.hasActiveCalls && !args.hasRingingCalls && !args.hasHoldingCalls
                            && !args.isTonePlaying) {
                        transitionTo(mUnfocusedState);
                    }
                default:
                    return NOT_HANDLED;
            }
        }
    }

    private static final String LOG_TAG = CallAudioModeStateMachine.class.getSimpleName();

    private final State mUnfocusedState = new UnfocusedState();
    private final State mRingingFocusState = new RingingFocusState();
    private final State mSimCallFocusState = new SimCallFocusState();
    private final State mVoipCallFocusState = new VoipCallFocusState();
    private final State mOtherFocusState = new OtherFocusState();

    private final Context mContext;
    private final AudioManager mAudioManager;
    private CallAudioManager mCallAudioManager;

    private int mMostRecentMode;
    private boolean mIsInitialized = false;

    public CallAudioModeStateMachine(Context context) {
        super(CallAudioModeStateMachine.class.getSimpleName());
        mContext = context;
        mAudioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        mMostRecentMode = AudioManager.MODE_NORMAL;

        addState(mUnfocusedState);
        addState(mRingingFocusState);
        addState(mSimCallFocusState);
        addState(mVoipCallFocusState);
        addState(mOtherFocusState);
        setInitialState(mUnfocusedState);
        start();
        sendMessage(INITIALIZE);
    }

    public void setCallAudioManager(CallAudioManager callAudioManager) {
        mCallAudioManager = callAudioManager;
    }

    @Override
    protected void onPreHandleMessage(Message msg) {
        if (msg.obj != null && msg.obj instanceof MessageArgs) {
            Log.continueSession(((MessageArgs) msg.obj).session, "CAMSM.pM_" + msg.what);
            Log.i(LOG_TAG, "Message received: %s.", MESSAGE_CODE_TO_NAME.get(msg.what));
        } else {
            Log.w(LOG_TAG, "Message sent must be of type nonnull MessageArgs, but got " +
                    (msg.obj == null ? "null" : msg.obj.getClass().getSimpleName()));
            Log.w(LOG_TAG, "The message was of code %d = %s",
                    msg.what, MESSAGE_CODE_TO_NAME.get(msg.what));
        }
    }

    @Override
    protected void onPostHandleMessage(Message msg) {
        Log.endSession();
    }

    @Override
    protected void unhandledMessage(Message msg) {
        // TODO: figure out if anything actually needs to be done here.
    }
}