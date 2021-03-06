/*
 * Kontalk Android client
 * Copyright (C) 2016 Kontalk Devteam <devteam@kontalk.org>

 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.

 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.

 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.kontalk.ui;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.afollestad.materialdialogs.DialogAction;
import com.afollestad.materialdialogs.MaterialDialog;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.util.XmppStringUtils;
import org.spongycastle.openpgp.PGPPublicKeyRing;

import android.app.Activity;
import android.content.Context;
import android.database.sqlite.SQLiteDiskIOException;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Toast;

import org.kontalk.BuildConfig;
import org.kontalk.R;
import org.kontalk.authenticator.Authenticator;
import org.kontalk.client.KontalkGroupManager;
import org.kontalk.crypto.PGP;
import org.kontalk.data.Contact;
import org.kontalk.data.Conversation;
import org.kontalk.message.CompositeMessage;
import org.kontalk.provider.MyMessages;
import org.kontalk.provider.MyMessages.Groups;
import org.kontalk.service.msgcenter.MessageCenterService;
import org.kontalk.util.XMPPUtils;


/**
 * Composer fragment for group chats.
 * @author Daniele Ricci
 */
public class GroupMessageFragment extends AbstractComposeFragment {
    private static final String TAG = ComposeMessage.TAG;

    /** The virtual or real group JID. */
    private String mGroupJID;

    private MenuItem mInviteGroupMenu;
    private MenuItem mSetGroupSubjectMenu;
    private MenuItem mLeaveGroupMenu;
    private MenuItem mAttachMenu;

    @Override
    public boolean sendInactive() {
        // TODO
        return false;
    }

    @Override
    protected void updateUI() {
        super.updateUI();
        if (mInviteGroupMenu != null) {
            boolean visible;
            String myUser = Authenticator.getSelfJID(getContext());

            // menu items requiring ownership and membership
            visible = KontalkGroupManager.KontalkGroup
                .checkOwnership(mConversation.getGroupJid(), myUser) &&
                mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
            mInviteGroupMenu.setVisible(visible);
            mInviteGroupMenu.setEnabled(visible);
            mSetGroupSubjectMenu.setVisible(visible);
            mSetGroupSubjectMenu.setEnabled(visible);

            // menu items requiring membership
            visible = mConversation.getGroupMembership() == Groups.MEMBERSHIP_MEMBER;
            mLeaveGroupMenu.setVisible(visible);
            mLeaveGroupMenu.setEnabled(visible);
            mAttachMenu.setVisible(visible);
            mAttachMenu.setEnabled(visible);
            if (!visible)
                tryHideAttachmentView();
        }
    }

    @Override
    protected void onInflateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.group_message_menu, menu);
        mInviteGroupMenu = menu.findItem(R.id.invite_group);
        mSetGroupSubjectMenu = menu.findItem(R.id.group_subject);
        mLeaveGroupMenu = menu.findItem(R.id.leave_group);
        mAttachMenu = menu.findItem(R.id.menu_attachment);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (super.onOptionsItemSelected(item))
            return true;

        switch (item.getItemId()) {
            case R.id.group_subject:
                changeGroupSubject();
                return true;
            case R.id.leave_group:
                leaveGroup();
                return true;
        }

        return false;
    }

    @Override
    protected void addUsers(String[] members) {
        Set<String> existingMembers = new HashSet<>();
        Collections.addAll(existingMembers, mConversation.getGroupPeers());

        // ensure no duplicates
        String selfJid = Authenticator.getSelfJID(getContext());
        Set<String> usersList = new HashSet<>();
        for (String member : members) {
            // exclude ourselves and do not add if already an existing member
            if (!member.equalsIgnoreCase(selfJid) && !existingMembers.contains(member))
                usersList.add(member);
        }

        if (usersList.size() > 0) {
            String[] users = usersList.toArray(new String[usersList.size()]);
            mConversation.addUsers(users);
            // reload conversation
            ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
        }
    }

    @Override
    protected void deleteConversation() {
        try {
            // this will also leave the group if true is passed
            mConversation.delete(false);
        }
        catch (SQLiteDiskIOException e) {
            Log.w(TAG, "error deleting thread");
            Toast.makeText(getActivity(), R.string.error_delete_thread,
                Toast.LENGTH_LONG).show();
        }
    }

    private void leaveGroup() {
        new MaterialDialog.Builder(getActivity())
            .content(R.string.confirm_will_leave_group)
            .positiveText(android.R.string.ok)
            .positiveColorRes(R.color.button_danger)
            .onPositive(new MaterialDialog.SingleButtonCallback() {
                @Override
                public void onClick(@NonNull MaterialDialog dialog, @NonNull DialogAction which) {
                    // leave group
                    if (dialog.isPromptCheckBoxChecked()) {
                        mConversation.delete(true);
                        // manually close the conversation
                        closeConversation();
                    }
                    else {
                        mConversation.leaveGroup();
                        // reload conversation
                        if (isVisible())
                            startQuery(false);
                    }
                }
            })
            .checkBoxPromptRes(R.string.leave_group_delete_messages, false, null)
            .negativeText(android.R.string.cancel)
            .show();
    }

    private void changeGroupSubject() {
        new MaterialDialog.Builder(getContext())
            .title(R.string.title_group_subject)
            .positiveText(android.R.string.ok)
            .negativeText(android.R.string.cancel)
            .input(null, mConversation.getGroupSubject(), true, new MaterialDialog.InputCallback() {
                @Override
                public void onInput(@NonNull MaterialDialog dialog, CharSequence input) {
                    setGroupSubject(!TextUtils.isEmpty(input) ? input.toString() : null);
                }
            })
            .inputRange(0, Groups.GROUP_SUBJECT_MAX_LENGTH)
            .show();
    }

    private void setGroupSubject(String subject) {
        mConversation.setGroupSubject(subject);
        // reload conversation
        ((ComposeMessageParent) getActivity()).loadConversation(getThreadId());
    }

    private void requestPresence() {
        Context context;
        if (mConversation != null && (context = getContext()) != null) {
            String[] users = mConversation.getGroupPeers();
            if (users != null) {
                for (String user : users) {
                    MessageCenterService.requestPresence(context, user);
                }
            }
        }
    }

    @Override
    protected String getDecodedPeer(CompositeMessage msg) {
        if (msg.getDirection() == MyMessages.Messages.DIRECTION_IN) {
            String userId = msg.getSender();
            Contact c = Contact.findByUserId(getContext(), userId);
            return c != null ? c.getNumber() : userId;
        }
        return null;
    }

    @Override
    protected String getDecodedName(CompositeMessage msg) {
        if (msg.getDirection() == MyMessages.Messages.DIRECTION_IN) {
            String userId = msg.getSender();
            Contact c = Contact.findByUserId(getContext(), userId);
            if (c != null)
                return c.getName();
        }
        return null;
    }

    @Override
    protected void loadConversationMetadata(Uri uri) {
        super.loadConversationMetadata(uri);
        if (mConversation != null) {
            mGroupJID = mConversation.getRecipient();
            mUserName = mGroupJID;
        }
    }

    /**
     * Since this is a group chat, it can be only opened from within the app.
     * No ACTION_VIEW intent ever will be delivered for this.
     */
    @Override
    protected void handleActionView(Uri uri) {
        throw new AssertionError("This shouldn't be called ever!");
    }

    /**
     * Used only during activity restore.
     */
    @Override
    protected void handleActionViewConversation(Uri uri, Bundle args) {
        mGroupJID = uri.getPathSegments().get(1);
        mConversation = Conversation.loadFromUserId(getActivity(), mGroupJID);
        // unlikely, but better safe than sorry
        if (mConversation == null) {
            Log.i(TAG, "conversation for " + mGroupJID + " not found - exiting");
            getActivity().finish();
        }

        setThreadId(mConversation.getThreadId());
        mUserName = mGroupJID;
    }

    @Override
    protected void onArgumentsProcessed() {
        // nothing
    }

    @Override
    protected void onConversationCreated() {
        // warning will be reloaded if necessary
        hideWarning();

        super.onConversationCreated();
        // set group title
        String subject = mConversation.getGroupSubject();
        if (TextUtils.isEmpty(subject))
            subject = getString(R.string.group_untitled);

        String status;
        boolean sendEnabled;
        int membership = mConversation.getGroupMembership();
        switch (membership) {
            case Groups.MEMBERSHIP_PARTED:
                status = getString(R.string.group_command_text_part_self);
                sendEnabled = false;
                break;
            case Groups.MEMBERSHIP_KICKED:
                status = getString(R.string.group_command_text_part_kicked);
                sendEnabled = false;
                break;
            case Groups.MEMBERSHIP_OBSERVER: {
                int count = mConversation.getGroupPeers().length;
                status = getResources()
                    .getQuantityString(R.plurals.group_people, count, count);
                sendEnabled = count > 1;
                break;
            }
            case Groups.MEMBERSHIP_MEMBER: {
                // +1 because we are not included in the members list
                int count = mConversation.getGroupPeers().length + 1;
                status = getResources()
                    .getQuantityString(R.plurals.group_people, count, count);
                sendEnabled = count > 1;
                break;
            }
            default:
                // shouldn't happen
                throw new RuntimeException("Unknown membership status: " + membership);
        }

        // disable sending if necessary
        mComposer.setSendEnabled(sendEnabled);

        setActivityTitle(subject, status);
    }

    private void showKeyWarning() {
        Activity context = getActivity();
        if (context != null) {
            showWarning(context.getText(R.string.warning_public_key_group_unverified), new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    viewGroupInfo();
                }
            }, WarningType.FATAL);
        }
    }

    @Override
    protected void onPresence(String jid, Presence.Type type, boolean removed, Presence.Mode mode, String fingerprint) {
        Context context = getContext();
        if (context == null)
            return;

        if (BuildConfig.DEBUG) {
            Log.d(TAG, "group member presence from " + jid + " (type=" + type + ", fingerprint=" + fingerprint + ")");
        }

        // handle null type - meaning no subscription (warn user)
        if (type == null) {
            // some users are missing subscription - disable sending
            // FIXME a toast isn't the right way to warn about this (discussion going on in #179)
            Toast.makeText(context,
                "You can't chat with some of the group members because you haven't been authorized yet. Open a private chat with unknown users first.",
                Toast.LENGTH_LONG).show();
            mComposer.setSendEnabled(false);
        }

        else if (type == Presence.Type.available || type == Presence.Type.unavailable) {
            String bareJid = XmppStringUtils.parseBareJid(jid);

            Contact contact = Contact.findByUserId(context, bareJid);
            if (contact != null) {
                // if this is null, we are accepting the key for the first time
                PGPPublicKeyRing trustedPublicKey = contact.getTrustedPublicKeyRing();

                // request the key if we don't have a trusted one and of course if the user has a key
                boolean unknownKey = (trustedPublicKey == null && contact.getFingerprint() != null);
                boolean changedKey = false;
                // check if fingerprint changed
                if (trustedPublicKey != null && fingerprint != null) {
                    String oldFingerprint = PGP.getFingerprint(PGP.getMasterKey(trustedPublicKey));
                    if (!fingerprint.equalsIgnoreCase(oldFingerprint)) {
                        // fingerprint has changed since last time
                        changedKey = true;
                    }
                }

                if (changedKey || unknownKey) {
                    showKeyWarning();
                }
            }

        }
    }

    @Override
    protected void onConnected() {
        // TODO
    }

    @Override
    protected void onRosterLoaded() {
        requestPresence();
    }

    @Override
    protected void onStartTyping(String jid) {
        // TODO
    }

    @Override
    protected void onStopTyping(String jid) {
        // TODO
    }

    @Override
    protected boolean isUserId(String jid) {
        if (mConversation != null) {
            String[] users = mConversation.getGroupPeers();
            if (users != null) {
                for (String user : users) {
                    if (XMPPUtils.equalsBareJID(jid, user))
                        return true;
                }
            }
        }
        return false;
    }

    @Override
    public String getUserId() {
        return mGroupJID;
    }

    @Override
    public boolean sendTyping() {
        // TODO
        return false;
    }

    public void viewGroupInfo() {
        int membership = mConversation != null ? mConversation.getGroupMembership() : Groups.MEMBERSHIP_PARTED;
        if (membership == Groups.MEMBERSHIP_MEMBER || membership == Groups.MEMBERSHIP_OBSERVER)
            // TODO tablet support
            GroupInfoActivity.start(getContext(), getThreadId());
    }

}
