/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2019 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.connectbot;

import com.linkedin.android.testbutler.TestButler;

import android.os.Bundle;
import androidx.test.runner.AndroidJUnitRunner;

public class ConnectbotJUnitRunner extends AndroidJUnitRunner {
		@Override
		public void onStart() {
			TestButler.setup(getTargetContext());
			super.onStart();
		}

		@Override
		public void finish(int resultCode, Bundle results) {
			TestButler.teardown(getTargetContext());
			super.finish(resultCode, results);
		}
}
