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

import com.pedro.srt.mpeg2ts.MpegTsPayload
import com.pedro.srt.utils.CRC32
import java.nio.ByteBuffer

/**
 * SCTE-35 splice_info_section (ANSI/SCTE 35).
 *
 * Encoded as a private section (table_id = 0xFC, section_syntax_indicator = 0).
 * Carries a splice_insert command (splice_command_type = 0x05).
 *
 * Binary layout written by [write]:
 *   pointer_field (1)
 *   table_id (1) = 0xFC
 *   section_syntax_indicator(0) + private_indicator(0) + reserved(2=0b11) + section_length(12) : 2 bytes
 *   protocol_version(8=0) : 1
 *   encrypted_packet(1=0) + encryption_algorithm(6=0) + pts_adjustment[32](1=0) : 1
 *   pts_adjustment[31:0] : 4
 *   cw_index(8=0xFF) : 1
 *   tier(12=0xFFF) + splice_command_length(12) : 3
 *   splice_command_type(8=0x05) : 1
 *   splice_insert() : commandSize bytes
 *   descriptor_loop_length(16=0) : 2
 *   CRC_32 : 4
 */
class Scte35Section(
  pid: Int,
  private val splice: Scte35SpliceInsert
) : MpegTsPayload(pid, false) {

  private val spliceImmediate = splice.ptsTime == null
  private val hasDuration = splice.duration != null

  /** Total bytes written by [write], including the leading pointer_field byte. */
  fun getSize(): Int = 1 + 3 + 11 + calculateCommandSize() + 2 + 4

  fun write(buffer: ByteBuffer) {
    buffer.put(0x00) // pointer_field
    val crcStart = buffer.position()

    val commandSize = calculateCommandSize()
    // section_length counts from protocol_version through CRC_32
    val sectionLength = 11 + commandSize + 2 + 4

    // table_id
    buffer.put(0xFC.toByte())
    // section_syntax_indicator(0) | private_indicator(0) | reserved(0b11) | section_length(12)
    buffer.putShort((0x3000 or (sectionLength and 0x0FFF)).toShort())
    // protocol_version
    buffer.put(0x00)
    // encrypted_packet(0,1bit) + encryption_algorithm(0,6bits) + pts_adjustment[32](0,1bit)
    buffer.put(0x00)
    // pts_adjustment[31:0] = 0
    buffer.putInt(0x00000000)
    // cw_index = 0xFF (no CW)
    buffer.put(0xFF.toByte())
    // tier(12=0xFFF) + splice_command_length(12)
    buffer.put(0xFF.toByte())                                     // tier[11:4]
    buffer.put((0xF0 or (commandSize shr 8 and 0x0F)).toByte())   // tier[3:0] + command_length[11:8]
    buffer.put((commandSize and 0xFF).toByte())                   // command_length[7:0]
    // splice_command_type = splice_insert
    buffer.put(0x05)

    writeSpliceInsert(buffer)

    // descriptor_loop_length = 0
    buffer.putShort(0x0000)

    // CRC_32
    val crc = CRC32.getCRC32(buffer.array(), crcStart, buffer.position())
    buffer.putInt(crc)
  }

  private fun writeSpliceInsert(buffer: ByteBuffer) {
    // splice_event_id (32 bits)
    buffer.putInt(splice.spliceEventId.toInt())
    // splice_event_cancel_indicator(0) + reserved(0x7F)
    buffer.put(0x7F)
    // out_of_network_indicator | program_splice_flag(1) | duration_flag | splice_immediate_flag | reserved(0xF)
    val outBit = if (splice.outOfNetwork) 1 else 0
    val durBit = if (hasDuration) 1 else 0
    val immBit = if (spliceImmediate) 1 else 0
    buffer.put(((outBit shl 7) or (1 shl 6) or (durBit shl 5) or (immBit shl 4) or 0x0F).toByte())

    if (!spliceImmediate) {
      // splice_time: time_specified_flag(1) + reserved(6=0x3F) + pts_time(33 bits)
      val pts = splice.ptsTime!!
      val ptsMsb = (pts ushr 32 and 1L).toInt()
      buffer.put((0x80 or 0x7E or ptsMsb).toByte()) // 0xFE | ptsMsb
      buffer.putInt((pts and 0xFFFFFFFFL).toInt())
    }

    if (hasDuration) {
      // break_duration: auto_return(1) + reserved(6=0x3F) + duration(33 bits)
      val dur = splice.duration!!
      val durMsb = (dur ushr 32 and 1L).toInt()
      buffer.put(((if (splice.autoReturn) 0x80 else 0x00) or 0x7E or durMsb).toByte())
      buffer.putInt((dur and 0xFFFFFFFFL).toInt())
    }

    buffer.putShort(splice.uniqueProgramId.toShort())
    buffer.put(splice.availNum.toByte())
    buffer.put(splice.availsExpected.toByte())
  }

  private fun calculateCommandSize(): Int {
    var size = 4 + 1 + 1 // splice_event_id + cancel_indicator_byte + flags_byte
    if (!spliceImmediate) size += 5  // splice_time with pts_time
    if (hasDuration) size += 5       // break_duration
    size += 2 + 1 + 1               // unique_program_id + avail_num + avails_expected
    return size
  }
}
