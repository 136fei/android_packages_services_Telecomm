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

package com.android.server.telecom.tests;

import org.mockito.ArgumentCaptor;

import android.telecom.CallAudioState;
import android.telecom.VideoProfile;

import java.util.List;

import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

/**
 * System tests for video-specific behavior in telecom.
 * TODO: Add unit tests which ensure that auto-speakerphone does not occur when using a wired
 * headset or a bluetooth headset.
 */
public class VideoCallTests extends TelecomSystemTest {

    /**
     * Tests to ensure an incoming video-call is automatically routed to the speakerphone when
     * the call is answered and neither a wired headset nor bluetooth headset are connected.
     */
    public void testAutoSpeakerphoneIncomingBidirectional() throws Exception {
        // Start an incoming video call.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_BIDIRECTIONAL);

        verifyAudioRoute(CallAudioState.ROUTE_SPEAKER, 2);
    }

    /**
     * Tests to ensure an incoming receive-only video-call is answered in speakerphone mode.  Note
     * that this is not a scenario we would expect normally with the default dialer as it will
     * always answer incoming video calls as bi-directional.  It is, however, possible for a third
     * party dialer to answer an incoming video call a a one-way video call.
     */
    public void testAutoSpeakerphoneIncomingReceiveOnly() throws Exception {
        // Start an incoming video call.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_RX_ENABLED);

        verifyAudioRoute(CallAudioState.ROUTE_SPEAKER, 2);
    }

    /**
     * Tests audio routing for an outgoing video call made with bidirectional video.  Expect to be
     * in speaker mode.
     */
    public void testAutoSpeakerphoneOutgoingBidirectional() throws Exception {
        // Start an incoming video call.
        IdPair ids = startAndMakeActiveOutgoingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_BIDIRECTIONAL);

        verifyAudioRoute(CallAudioState.ROUTE_SPEAKER, 2);
    }

    /**
     * Tests audio routing for an outgoing video call made with transmit only video.  Expect to be
     * in speaker mode.  Note: The default UI does not support making one-way video calls, but the
     * APIs do and a third party incall UI could choose to support that.
     */
    public void testAutoSpeakerphoneOutgoingTransmitOnly() throws Exception {
        // Start an incoming video call.
        IdPair ids = startAndMakeActiveOutgoingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_TX_ENABLED);

        verifyAudioRoute(CallAudioState.ROUTE_SPEAKER, 2);
    }

    /**
     * Tests audio routing for an outgoing video call made with transmit only video.  Expect to be
     * in speaker mode.  Note: The default UI does not support making one-way video calls, but the
     * APIs do and a third party incall UI could choose to support that.
     */
    public void testNoAutoSpeakerphoneOnOutgoing() throws Exception {
        // Start an incoming video call.
        IdPair ids = startAndMakeActiveOutgoingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_AUDIO_ONLY);

        verifyAudioRoute(CallAudioState.ROUTE_EARPIECE, 1);
    }

    /**
     * Tests to ensure an incoming audio-only call is routed to the earpiece.
     */
    public void testNoAutoSpeakerphoneOnIncoming() throws Exception {

        // Start an incoming video call.
        IdPair ids = startAndMakeActiveIncomingCall("650-555-1212",
                mPhoneAccountA0.getAccountHandle(), mConnectionServiceFixtureA,
                VideoProfile.STATE_AUDIO_ONLY);

        verifyAudioRoute(CallAudioState.ROUTE_EARPIECE, 1);
    }

    /**
     * Verifies that the
     * {@link android.telecom.InCallService#onCallAudioStateChanged(CallAudioState)} change is
     * called with an expected route and number of changes.
     *
     * @param expectedRoute The expected audio route on the latest change.
     * @param audioStateChangeCount The number of audio state changes expected.  This is set based
     *                              on how many times we expect the audio route to change when
     *                              setting up a call.  For an audio-only call, we normally expect
     *                              1 route change, and for a video call we expect an extra change.
     */
    private void verifyAudioRoute(int expectedRoute, int audioStateChangeCount) throws Exception {
        // Capture all onCallAudioStateChanged callbacks to InCall.
        ArgumentCaptor<CallAudioState> callAudioStateArgumentCaptor = ArgumentCaptor.forClass(
                CallAudioState.class);
        verify(mInCallServiceFixtureX.getTestDouble(),
                timeout(TEST_TIMEOUT).times(audioStateChangeCount)).
                onCallAudioStateChanged(callAudioStateArgumentCaptor.capture());
        List<CallAudioState> changes = callAudioStateArgumentCaptor.getAllValues();
        assertEquals(expectedRoute, changes.get(changes.size() - 1).getRoute());
    }
}
