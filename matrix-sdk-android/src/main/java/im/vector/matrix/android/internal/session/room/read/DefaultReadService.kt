/*
 * Copyright 2019 New Vector Ltd
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

package im.vector.matrix.android.internal.session.room.read

import androidx.lifecycle.LiveData
import com.zhuinden.monarchy.Monarchy
import im.vector.matrix.android.api.MatrixCallback
import im.vector.matrix.android.api.session.room.members.RoomMembersService
import im.vector.matrix.android.api.session.room.model.ReadReceipt
import im.vector.matrix.android.api.session.room.read.ReadService
import im.vector.matrix.android.internal.database.model.EventEntity
import im.vector.matrix.android.internal.database.model.ReadReceiptEntity
import im.vector.matrix.android.internal.database.model.ReadReceiptEntityFields
import im.vector.matrix.android.internal.database.query.latestEvent
import im.vector.matrix.android.internal.database.query.where
import im.vector.matrix.android.internal.task.TaskExecutor
import im.vector.matrix.android.internal.task.configureWith
import im.vector.matrix.android.internal.util.fetchCopied

internal class DefaultReadService(private val roomId: String,
                                  private val monarchy: Monarchy,
                                  private val roomMembersService: RoomMembersService,
                                  private val setReadMarkersTask: SetReadMarkersTask,
                                  private val taskExecutor: TaskExecutor) : ReadService {

    override fun markAllAsRead(callback: MatrixCallback<Void>) {
        val latestEvent = monarchy.fetchCopied { EventEntity.latestEvent(it, roomId) }
        val params = SetReadMarkersTask.Params(roomId, fullyReadEventId = latestEvent?.eventId, readReceiptEventId = latestEvent?.eventId)
        setReadMarkersTask.configureWith(params).executeBy(taskExecutor)
    }

    override fun setReadReceipt(eventId: String, callback: MatrixCallback<Void>) {
        val params = SetReadMarkersTask.Params(roomId, fullyReadEventId = null, readReceiptEventId = eventId)
        setReadMarkersTask.configureWith(params).executeBy(taskExecutor)
    }

    override fun setReadMarker(fullyReadEventId: String, callback: MatrixCallback<Void>) {
        val params = SetReadMarkersTask.Params(roomId, fullyReadEventId = fullyReadEventId, readReceiptEventId = null)
        setReadMarkersTask.configureWith(params).executeBy(taskExecutor)
    }

    override fun readReceipts(): LiveData<List<ReadReceipt>> {
        return monarchy.findAllMappedWithChanges(
                { realm -> ReadReceiptEntity.where(realm, roomId) },
                {
                    val roomMember = roomMembersService.getRoomMember(it.userId)
                    ReadReceipt(roomMember, it.eventId, it.originServerTs.toLong())
                }
        )
    }

    override fun readReceipts(eventId: String): List<ReadReceipt> {
        return monarchy.fetchAllMappedSync(
                { realm ->
                    ReadReceiptEntity.where(realm, roomId)
                            .equalTo(ReadReceiptEntityFields.EVENT_ID, eventId)
                },
                {
                    val roomMember = roomMembersService.getRoomMember(it.userId)
                    ReadReceipt(roomMember, it.eventId, it.originServerTs.toLong())
                }
        )
    }
}