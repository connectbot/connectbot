/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2026 Kenny Root
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

package org.connectbot.util;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.connectbot.terminal.TerminalEmulator;
import org.connectbot.terminal.TerminalEmulatorImpl;
import org.connectbot.terminal.TerminalLine;
import org.connectbot.terminal.TerminalSnapshot;

/**
 * Reads the full contents (scrollback plus visible screen) of a terminal session.
 *
 * <p>termlib does not yet expose a public API for reading the scrollback buffer; its snapshot types
 * are Kotlin {@code internal}. That visibility is only enforced by the Kotlin compiler, not in
 * bytecode, so this accessor must be written in Java. It can be replaced once termlib grows a
 * public transcript API.
 */
public final class TerminalSessionReader {
  private TerminalSessionReader() {}

  /**
   * Returns every line of the session, oldest scrollback line first, ending with the visible
   * screen. Returns an empty list if the emulator does not expose its contents.
   */
  public static List<TerminalSessionLine> readSessionLines(TerminalEmulator emulator) {
    if (!(emulator instanceof TerminalEmulatorImpl)) {
      return Collections.emptyList();
    }
    TerminalSnapshot snapshot = ((TerminalEmulatorImpl) emulator).getSnapshot$lib().getValue();
    List<TerminalSessionLine> sessionLines = new ArrayList<>();
    appendLines(sessionLines, snapshot.getScrollback());
    appendLines(sessionLines, snapshot.getLines());
    return sessionLines;
  }

  private static void appendLines(
      List<TerminalSessionLine> sessionLines, List<TerminalLine> lines) {
    for (TerminalLine line : lines) {
      sessionLines.add(new TerminalSessionLine(line.getText(), line.getSoftWrapped()));
    }
  }
}
