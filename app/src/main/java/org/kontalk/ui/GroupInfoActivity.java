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

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.MenuItem;

import org.kontalk.R;
import org.kontalk.provider.MyMessages.Messages;


/**
 * Group information activity.
 * @author Daniele Ricci
 */
public class GroupInfoActivity extends ToolbarActivity implements GroupInfoFragment.GroupInfoParent {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.group_info_screen);

        setupToolbar(true);

        if (savedInstanceState == null) {
            Intent i = getIntent();
            long threadId = i.getLongExtra("conversation", Messages.NO_THREAD);
            Fragment f = GroupInfoFragment.newInstance(threadId);
            getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment, f)
                .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                finish();
                return true;
        }

        return super.onOptionsItemSelected(item);
    }

    public static void start(Context context, long threadId) {
        Intent intent = new Intent(context, GroupInfoActivity.class);
        intent.putExtra("conversation", threadId);
        context.startActivity(intent);
    }

    @Override
    public void dismiss() {
        finish();
    }

}
