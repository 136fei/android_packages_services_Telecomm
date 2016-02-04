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
 * limitations under the License.
 */

package com.android.server.telecom.tests;


import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyBoolean;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.anyString;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.media.IAudioService;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Process;
import android.os.UserHandle;
import android.telecom.Call;
import android.telecom.ConnectionRequest;
import android.telecom.ParcelableCall;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;
import android.telecom.VideoProfile;

import com.android.internal.telecom.IInCallAdapter;
import com.android.server.telecom.BluetoothPhoneServiceImpl;
import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallerInfoAsyncQueryFactory;
import com.android.server.telecom.CallsManager;
import com.android.server.telecom.CallsManagerListenerBase;
import com.android.server.telecom.ContactsAsyncHelper;
import com.android.server.telecom.HeadsetMediaButton;
import com.android.server.telecom.HeadsetMediaButtonFactory;
import com.android.server.telecom.InCallWakeLockController;
import com.android.server.telecom.InCallWakeLockControllerFactory;
import com.android.server.telecom.MissedCallNotifier;
import com.android.server.telecom.PhoneAccountRegistrar;
import com.android.server.telecom.ProximitySensorManager;
import com.android.server.telecom.ProximitySensorManagerFactory;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.components.UserCallIntentProcessor;
import com.android.server.telecom.ui.MissedCallNotifierImpl.MissedCallNotifierImplFactory;

import com.google.common.base.Predicate;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

/**
 * Implements mocks and functionality required to implement telecom system tests.
 */
public class TelecomSystemTest extends TelecomTestCase {

    static final int TEST_POLL_INTERVAL = 10;  // milliseconds
    static final int TEST_TIMEOUT = 1000;  // milliseconds

    public class HeadsetMediaButtonFactoryF implements HeadsetMediaButtonFactory  {
        @Override
        public HeadsetMediaButton create(Context context, CallsManager callsManager,
                TelecomSystem.SyncRoot lock) {
            return mHeadsetMediaButton;
        }
    }

    public class ProximitySensorManagerFactoryF implements ProximitySensorManagerFactory {
        @Override
        public ProximitySensorManager create(Context context, CallsManager callsManager) {
            return mProximitySensorManager;
        }
    }

    public class InCallWakeLockControllerFactoryF implements InCallWakeLockControllerFactory {
        @Override
        public InCallWakeLockController create(Context context, CallsManager callsManager) {
            return mInCallWakeLockController;
        }
    }

    public static class MissedCallNotifierFakeImpl extends CallsManagerListenerBase
            implements MissedCallNotifier {
        @Override
        public void clearMissedCalls(UserHandle userHandle) {

        }

        @Override
        public void showMissedCallNotification(com.android.server.telecom.Call call) {

        }

        @Override
        public void reloadFromDatabase(TelecomSystem.SyncRoot lock, CallsManager callsManager,
                ContactsAsyncHelper contactsAsyncHelper,
                CallerInfoAsyncQueryFactory callerInfoAsyncQueryFactory, UserHandle userHandle) {

        }

        @Override
        public void setCurrentUserHandle(UserHandle userHandle) {

        }
    }

    MissedCallNotifier mMissedCallNotifier = new MissedCallNotifierFakeImpl();
    @Mock HeadsetMediaButton mHeadsetMediaButton;
    @Mock ProximitySensorManager mProximitySensorManager;
    @Mock InCallWakeLockController mInCallWakeLockController;
    @Mock BluetoothPhoneServiceImpl mBluetoothPhoneServiceImpl;

    final ComponentName mInCallServiceComponentNameX =
            new ComponentName(
                    "incall-service-package-X",
                    "incall-service-class-X");
    final ComponentName mInCallServiceComponentNameY =
            new ComponentName(
                    "incall-service-package-Y",
                    "incall-service-class-Y");

    InCallServiceFixture mInCallServiceFixtureX;
    InCallServiceFixture mInCallServiceFixtureY;

    final ComponentName mConnectionServiceComponentNameA =
            new ComponentName(
                    "connection-service-package-A",
                    "connection-service-class-A");
    final ComponentName mConnectionServiceComponentNameB =
            new ComponentName(
                    "connection-service-package-B",
                    "connection-service-class-B");

    final PhoneAccount mPhoneAccountA0 =
            PhoneAccount.builder(
                    new PhoneAccountHandle(
                            mConnectionServiceComponentNameA,
                            "id A 0"),
                    "Phone account service A ID 0")
                    .addSupportedUriScheme("tel")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_CALL_PROVIDER |
                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    .build();
    final PhoneAccount mPhoneAccountA1 =
            PhoneAccount.builder(
                    new PhoneAccountHandle(
                            mConnectionServiceComponentNameA,
                            "id A 1"),
                    "Phone account service A ID 1")
                    .addSupportedUriScheme("tel")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_CALL_PROVIDER |
                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    .build();
    final PhoneAccount mPhoneAccountB0 =
            PhoneAccount.builder(
                    new PhoneAccountHandle(
                            mConnectionServiceComponentNameB,
                            "id B 0"),
                    "Phone account service B ID 0")
                    .addSupportedUriScheme("tel")
                    .setCapabilities(
                            PhoneAccount.CAPABILITY_CALL_PROVIDER |
                            PhoneAccount.CAPABILITY_SIM_SUBSCRIPTION)
                    .build();

    ConnectionServiceFixture mConnectionServiceFixtureA;
    ConnectionServiceFixture mConnectionServiceFixtureB;

    CallerInfoAsyncQueryFactoryFixture mCallerInfoAsyncQueryFactoryFixture;

    IAudioService mAudioService;

    TelecomSystem mTelecomSystem;

    Context mSpyContext;

    private int mNumOutgoingCallsMade;

    class IdPair {
        final String mConnectionId;
        final String mCallId;

        public IdPair(String connectionId, String callId) {
            this.mConnectionId = connectionId;
            this.mCallId = callId;
        }
    }

    @Override
    public void setUp() throws Exception {
        super.setUp();
        mSpyContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        doReturn(mSpyContext).when(mSpyContext).getApplicationContext();

        mNumOutgoingCallsMade = 0;

        // First set up information about the In-Call services in the mock Context, since
        // Telecom will search for these as soon as it is instantiated
        setupInCallServices();

        // Next, create the TelecomSystem, our system under test
        setupTelecomSystem();

        // Finally, register the ConnectionServices with the PhoneAccountRegistrar of the
        // now-running TelecomSystem
        setupConnectionServices();
    }

    @Override
    public void tearDown() throws Exception {
        mTelecomSystem = null;
        super.tearDown();
    }

    private void setupTelecomSystem() throws Exception {
        // Use actual implementations instead of mocking the interface out.
        HeadsetMediaButtonFactory headsetMediaButtonFactory =
                spy(new HeadsetMediaButtonFactoryF());
        ProximitySensorManagerFactory proximitySensorManagerFactory =
                spy(new ProximitySensorManagerFactoryF());
        InCallWakeLockControllerFactory inCallWakeLockControllerFactory =
                spy(new InCallWakeLockControllerFactoryF());
        mAudioService = setupAudioService();

        mCallerInfoAsyncQueryFactoryFixture = new CallerInfoAsyncQueryFactoryFixture();

        mTelecomSystem = new TelecomSystem(
                mComponentContextFixture.getTestDouble(),
                new MissedCallNotifierImplFactory() {
                    @Override
                    public MissedCallNotifier makeMissedCallNotifierImpl(Context context,
                            PhoneAccountRegistrar phoneAccountRegistrar) {
                        return mMissedCallNotifier;
                    }
                },
                mCallerInfoAsyncQueryFactoryFixture.getTestDouble(),
                headsetMediaButtonFactory,
                proximitySensorManagerFactory,
                inCallWakeLockControllerFactory,
                new CallAudioManager.AudioServiceFactory() {
                    @Override
                    public IAudioService getAudioService() {
                        return mAudioService;
                    }
                },
                new BluetoothPhoneServiceImpl.BluetoothPhoneServiceImplFactory() {
                    @Override
                    public BluetoothPhoneServiceImpl makeBluetoothPhoneServiceImpl(Context context,
                            TelecomSystem.SyncRoot lock, CallsManager callsManager,
                            PhoneAccountRegistrar phoneAccountRegistrar) {
                        return mBluetoothPhoneServiceImpl;
                    }
                });

        mComponentContextFixture.setTelecomManager(new TelecomManager(
                mComponentContextFixture.getTestDouble(),
                mTelecomSystem.getTelecomServiceImpl().getBinder()));

        verify(headsetMediaButtonFactory).create(
                eq(mComponentContextFixture.getTestDouble().getApplicationContext()),
                any(CallsManager.class),
                any(TelecomSystem.SyncRoot.class));
        verify(proximitySensorManagerFactory).create(
                eq(mComponentContextFixture.getTestDouble().getApplicationContext()),
                any(CallsManager.class));
        verify(inCallWakeLockControllerFactory).create(
                eq(mComponentContextFixture.getTestDouble().getApplicationContext()),
                any(CallsManager.class));
    }

    private void setupConnectionServices() throws Exception {
        mConnectionServiceFixtureA = new ConnectionServiceFixture();
        mConnectionServiceFixtureB = new ConnectionServiceFixture();

        mComponentContextFixture.addConnectionService(mConnectionServiceComponentNameA,
                mConnectionServiceFixtureA.getTestDouble());
        mComponentContextFixture.addConnectionService(mConnectionServiceComponentNameB,
                mConnectionServiceFixtureB.getTestDouble());

        mTelecomSystem.getPhoneAccountRegistrar().registerPhoneAccount(mPhoneAccountA0);
        mTelecomSystem.getPhoneAccountRegistrar().registerPhoneAccount(mPhoneAccountA1);
        mTelecomSystem.getPhoneAccountRegistrar().registerPhoneAccount(mPhoneAccountB0);

        mTelecomSystem.getPhoneAccountRegistrar().setUserSelectedOutgoingPhoneAccount(
                mPhoneAccountA0.getAccountHandle(), Process.myUserHandle());
    }

    private void setupInCallServices() throws Exception {
        mComponentContextFixture.putResource(
                com.android.server.telecom.R.string.ui_default_package,
                mInCallServiceComponentNameX.getPackageName());
        mComponentContextFixture.putResource(
                com.android.server.telecom.R.string.incall_default_class,
                mInCallServiceComponentNameX.getClassName());
        mComponentContextFixture.putBooleanResource(
                com.android.internal.R.bool.config_voice_capable, true);

        mInCallServiceFixtureX = new InCallServiceFixture();
        mInCallServiceFixtureY = new InCallServiceFixture();

        mComponentContextFixture.addInCallService(mInCallServiceComponentNameX,
                mInCallServiceFixtureX.getTestDouble());
        mComponentContextFixture.addInCallService(mInCallServiceComponentNameY,
                mInCallServiceFixtureY.getTestDouble());
    }

    /**
     * Helper method for setting up the fake audio service.
     * Calls to the fake audio service need to toggle the return
     * value of AudioManager#isMicrophoneMute.
     * @return mock of IAudioService
     */
    private IAudioService setupAudioService() {
        IAudioService audioService = mock(IAudioService.class);

        final AudioManager fakeAudioManager =
                (AudioManager) mComponentContextFixture.getTestDouble()
                        .getApplicationContext().getSystemService(Context.AUDIO_SERVICE);

        try {
            doAnswer(new Answer() {
                @Override
                public Object answer(InvocationOnMock i) {
                    Object[] args = i.getArguments();
                    doReturn(args[0]).when(fakeAudioManager).isMicrophoneMute();
                    return null;
                }
            }).when(audioService)
                    .setMicrophoneMute(any(Boolean.class), any(String.class), any(Integer.class));

        } catch (android.os.RemoteException e) {
            // Do nothing, leave the faked microphone state as-is
        }
        return audioService;
    }

    protected IdPair startOutgoingPhoneCall(String number, PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture, UserHandle initiatingUser)
            throws Exception {
        return startOutgoingPhoneCall(number, phoneAccountHandle, connectionServiceFixture,
                initiatingUser, VideoProfile.STATE_AUDIO_ONLY);
    }

    protected IdPair startOutgoingPhoneCall(String number, PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture, UserHandle initiatingUser,
            int videoState) throws Exception {
        reset(connectionServiceFixture.getTestDouble(), mInCallServiceFixtureX.getTestDouble(),
                mInCallServiceFixtureY.getTestDouble());

        assertEquals(mInCallServiceFixtureX.mCallById.size(),
                mInCallServiceFixtureY.mCallById.size());
        assertEquals((mInCallServiceFixtureX.mInCallAdapter != null),
                (mInCallServiceFixtureY.mInCallAdapter != null));

        mNumOutgoingCallsMade++;
        int startingNumConnections = connectionServiceFixture.mConnectionById.size();
        int startingNumCalls = mInCallServiceFixtureX.mCallById.size();
        boolean hasInCallAdapter = mInCallServiceFixtureX.mInCallAdapter != null;

        Intent actionCallIntent = new Intent();
        actionCallIntent.setData(Uri.parse("tel:" + number));
        actionCallIntent.putExtra(Intent.EXTRA_PHONE_NUMBER, number);
        actionCallIntent.setAction(Intent.ACTION_CALL);
        if (phoneAccountHandle != null) {
            actionCallIntent.putExtra(
                    TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE,
                    phoneAccountHandle);
        }
        if (videoState != VideoProfile.STATE_AUDIO_ONLY) {
            actionCallIntent.putExtra(TelecomManager.EXTRA_START_CALL_WITH_VIDEO_STATE, videoState);
        }

        final UserHandle userHandle = initiatingUser;
        Context localAppContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        new UserCallIntentProcessor(localAppContext, userHandle).processIntent(
                actionCallIntent, null, true /* hasCallAppOp*/);
        // UserCallIntentProcessor's mContext.sendBroadcastAsUser(...) will call to an empty method
        // as to not actually try to send an intent to PrimaryCallReceiver. We verify that it was
        // called correctly in order to continue.
        verify(localAppContext).sendBroadcastAsUser(actionCallIntent, UserHandle.SYSTEM);
        mTelecomSystem.getCallIntentProcessor().processIntent(actionCallIntent);

        if (!hasInCallAdapter) {
            verify(mInCallServiceFixtureX.getTestDouble())
                    .setInCallAdapter(
                            any(IInCallAdapter.class));
            verify(mInCallServiceFixtureY.getTestDouble())
                    .setInCallAdapter(
                            any(IInCallAdapter.class));
        }

        ArgumentCaptor<Intent> newOutgoingCallIntent =
                ArgumentCaptor.forClass(Intent.class);
        ArgumentCaptor<BroadcastReceiver> newOutgoingCallReceiver =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        verify(mComponentContextFixture.getTestDouble().getApplicationContext(),
                times(mNumOutgoingCallsMade))
                .sendOrderedBroadcastAsUser(
                        newOutgoingCallIntent.capture(),
                        any(UserHandle.class),
                        anyString(),
                        anyInt(),
                        newOutgoingCallReceiver.capture(),
                        any(Handler.class),
                        anyInt(),
                        anyString(),
                        any(Bundle.class));

        // Pass on the new outgoing call Intent
        // Set a dummy PendingResult so the BroadcastReceiver agrees to accept onReceive()
        newOutgoingCallReceiver.getValue().setPendingResult(
                new BroadcastReceiver.PendingResult(0, "", null, 0, true, false, null, 0, 0));
        newOutgoingCallReceiver.getValue().setResultData(
                newOutgoingCallIntent.getValue().getStringExtra(Intent.EXTRA_PHONE_NUMBER));
        newOutgoingCallReceiver.getValue().onReceive(mComponentContextFixture.getTestDouble(),
                newOutgoingCallIntent.getValue());

        assertEquals(startingNumConnections + 1, connectionServiceFixture.mConnectionById.size());

        verify(connectionServiceFixture.getTestDouble())
                .createConnection(eq(phoneAccountHandle), anyString(), any(ConnectionRequest.class),
                        anyBoolean(), anyBoolean());
        connectionServiceFixture.sendHandleCreateConnectionComplete(
                connectionServiceFixture.mLatestConnectionId);

        assertEquals(startingNumCalls + 1, mInCallServiceFixtureX.mCallById.size());
        assertEquals(startingNumCalls + 1, mInCallServiceFixtureY.mCallById.size());

        assertEquals(mInCallServiceFixtureX.mLatestCallId, mInCallServiceFixtureY.mLatestCallId);

        return new IdPair(connectionServiceFixture.mLatestConnectionId,
                mInCallServiceFixtureX.mLatestCallId);
    }

    protected IdPair startIncomingPhoneCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            final ConnectionServiceFixture connectionServiceFixture) throws Exception {
        return startIncomingPhoneCall(number, phoneAccountHandle, VideoProfile.STATE_AUDIO_ONLY,
                connectionServiceFixture);
    }

    protected IdPair startIncomingPhoneCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            int videoState,
            final ConnectionServiceFixture connectionServiceFixture) throws Exception {
        reset(connectionServiceFixture.getTestDouble(), mInCallServiceFixtureX.getTestDouble(),
                mInCallServiceFixtureY.getTestDouble());

        assertEquals(mInCallServiceFixtureX.mCallById.size(),
                mInCallServiceFixtureY.mCallById.size());
        assertEquals((mInCallServiceFixtureX.mInCallAdapter != null),
                (mInCallServiceFixtureY.mInCallAdapter != null));
        final int startingNumConnections = connectionServiceFixture.mConnectionById.size();
        final int startingNumCalls = mInCallServiceFixtureX.mCallById.size();
        boolean hasInCallAdapter = mInCallServiceFixtureX.mInCallAdapter != null;
        connectionServiceFixture.mConnectionServiceDelegate.mVideoState = videoState;

        Bundle extras = new Bundle();
        extras.putParcelable(
                TelecomManager.EXTRA_INCOMING_CALL_ADDRESS,
                Uri.fromParts(PhoneAccount.SCHEME_TEL, number, null));
        mTelecomSystem.getTelecomServiceImpl().getBinder()
                .addNewIncomingCall(phoneAccountHandle, extras);

        verify(connectionServiceFixture.getTestDouble())
                .createConnection(any(PhoneAccountHandle.class), anyString(),
                        any(ConnectionRequest.class), eq(true), eq(false));

        connectionServiceFixture.sendSetRinging(connectionServiceFixture.mLatestConnectionId);

        for (CallerInfoAsyncQueryFactoryFixture.Request request :
                mCallerInfoAsyncQueryFactoryFixture.mRequests) {
            request.reply();
        }

        // For the case of incoming calls, Telecom connecting the InCall services and adding the
        // Call is triggered by the async completion of the CallerInfoAsyncQuery. Once the Call
        // is added, future interactions as triggered by the ConnectionService, through the various
        // test fixtures, will be synchronous.

        if (!hasInCallAdapter) {
            verify(mInCallServiceFixtureX.getTestDouble(), timeout(TEST_TIMEOUT))
                    .setInCallAdapter(any(IInCallAdapter.class));
            verify(mInCallServiceFixtureY.getTestDouble(), timeout(TEST_TIMEOUT))
                    .setInCallAdapter(any(IInCallAdapter.class));
        }

        // Give the InCallService time to respond

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mInCallServiceFixtureX.mInCallAdapter != null;
            }
        });

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return mInCallServiceFixtureY.mInCallAdapter != null;
            }
        });

        verify(mInCallServiceFixtureX.getTestDouble(), timeout(TEST_TIMEOUT))
                .addCall(any(ParcelableCall.class));
        verify(mInCallServiceFixtureY.getTestDouble(), timeout(TEST_TIMEOUT))
                .addCall(any(ParcelableCall.class));

        // Give the InCallService time to respond

        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return startingNumConnections + 1 ==
                        connectionServiceFixture.mConnectionById.size();
            }
        });
        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return startingNumCalls + 1 == mInCallServiceFixtureX.mCallById.size();
            }
        });
        assertTrueWithTimeout(new Predicate<Void>() {
            @Override
            public boolean apply(Void v) {
                return startingNumCalls + 1 == mInCallServiceFixtureY.mCallById.size();
            }
        });

        assertEquals(mInCallServiceFixtureX.mLatestCallId, mInCallServiceFixtureY.mLatestCallId);

        return new IdPair(connectionServiceFixture.mLatestConnectionId,
                mInCallServiceFixtureX.mLatestCallId);
    }

    protected IdPair startAndMakeActiveOutgoingCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture) throws Exception {
        return startAndMakeActiveOutgoingCall(number, phoneAccountHandle, connectionServiceFixture,
                VideoProfile.STATE_AUDIO_ONLY);
    }

    // A simple outgoing call, verifying that the appropriate connection service is contacted,
    // the proper lifecycle is followed, and both In-Call Services are updated correctly.
    protected IdPair startAndMakeActiveOutgoingCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture, int videoState) throws Exception {
        IdPair ids = startOutgoingPhoneCall(number, phoneAccountHandle, connectionServiceFixture,
                Process.myUserHandle(), videoState);

        connectionServiceFixture.sendSetDialing(ids.mConnectionId);
        assertEquals(Call.STATE_DIALING, mInCallServiceFixtureX.getCall(ids.mCallId).getState());
        assertEquals(Call.STATE_DIALING, mInCallServiceFixtureY.getCall(ids.mCallId).getState());

        connectionServiceFixture.sendSetVideoState(ids.mConnectionId);

        connectionServiceFixture.sendSetActive(ids.mConnectionId);
        assertEquals(Call.STATE_ACTIVE, mInCallServiceFixtureX.getCall(ids.mCallId).getState());
        assertEquals(Call.STATE_ACTIVE, mInCallServiceFixtureY.getCall(ids.mCallId).getState());

        return ids;
    }

    protected IdPair startAndMakeActiveIncomingCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture) throws Exception {
        return startAndMakeActiveIncomingCall(number, phoneAccountHandle, connectionServiceFixture,
                VideoProfile.STATE_AUDIO_ONLY);
    }

    // A simple incoming call, similar in scope to the previous test
    protected IdPair startAndMakeActiveIncomingCall(
            String number,
            PhoneAccountHandle phoneAccountHandle,
            ConnectionServiceFixture connectionServiceFixture,
            int videoState) throws Exception {
        IdPair ids = startIncomingPhoneCall(number, phoneAccountHandle, connectionServiceFixture);

        assertEquals(Call.STATE_RINGING, mInCallServiceFixtureX.getCall(ids.mCallId).getState());
        assertEquals(Call.STATE_RINGING, mInCallServiceFixtureY.getCall(ids.mCallId).getState());

        mInCallServiceFixtureX.mInCallAdapter
                .answerCall(ids.mCallId, videoState);

        if (!VideoProfile.isVideo(videoState)) {
            verify(connectionServiceFixture.getTestDouble())
                    .answer(ids.mConnectionId);
        } else {
            verify(connectionServiceFixture.getTestDouble())
                    .answerVideo(ids.mConnectionId, videoState);
        }

        connectionServiceFixture.sendSetActive(ids.mConnectionId);
        assertEquals(Call.STATE_ACTIVE, mInCallServiceFixtureX.getCall(ids.mCallId).getState());
        assertEquals(Call.STATE_ACTIVE, mInCallServiceFixtureY.getCall(ids.mCallId).getState());

        return ids;
    }

    protected static void assertTrueWithTimeout(Predicate<Void> predicate) {
        int elapsed = 0;
        while (elapsed < TEST_TIMEOUT) {
            if (predicate.apply(null)) {
                return;
            } else {
                try {
                    Thread.sleep(TEST_POLL_INTERVAL);
                    elapsed += TEST_POLL_INTERVAL;
                } catch (InterruptedException e) {
                    fail(e.toString());
                }
            }
        }
        fail("Timeout in assertTrueWithTimeout");
    }
}
