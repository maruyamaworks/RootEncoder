/*
 * Copyright (C) 2024 pedroSG94.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.pedro.srt.mpeg2ts.scte35

/**
 * Represents a SCTE-35 splice_insert command to be injected into the MPEG-TS stream.
 *
 * @param spliceEventId  32-bit unsigned event identifier (unique per splice pair).
 * @param outOfNetwork   true = entering ad break (out-of-network), false = returning to network.
 * @param ptsTime        33-bit PTS splice point; null means splice_immediate_flag = 1.
 * @param duration       33-bit break duration in 90 kHz ticks; null = no duration specified.
 * @param autoReturn     when duration != null: true = auto-return to network after duration.
 * @param uniqueProgramId 16-bit unique program identifier.
 * @param availNum       8-bit identification for a specific avail within one unique_program_id.
 * @param availsExpected 8-bit expected number of individual avails within the current viewing event.
 */
data class Scte35SpliceInsert(
  val spliceEventId: Long,
  val outOfNetwork: Boolean = true,
  val ptsTime: Long? = null,
  val duration: Long? = null,
  val autoReturn: Boolean = true,
  val uniqueProgramId: Int = 0,
  val availNum: Int = 0,
  val availsExpected: Int = 0
)
