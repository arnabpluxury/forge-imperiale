package com.forge.imperiale.data.local

import androidx.room.TypeConverter
import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.EffortLevel
import com.forge.imperiale.data.local.entity.MealStatus
import com.forge.imperiale.data.local.entity.ReminderCategory
import com.forge.imperiale.data.local.entity.VideoStreamType

class ForgeTypeConverters {
    @TypeConverter
    fun fromBlockType(value: BlockType): String = value.name

    @TypeConverter
    fun toBlockType(value: String): BlockType = BlockType.valueOf(value)

    @TypeConverter
    fun fromVideoStreamType(value: VideoStreamType): String = value.name

    @TypeConverter
    fun toVideoStreamType(value: String): VideoStreamType = VideoStreamType.valueOf(value)

    @TypeConverter
    fun fromEffortLevel(value: EffortLevel): String = value.name

    @TypeConverter
    fun toEffortLevel(value: String): EffortLevel = EffortLevel.valueOf(value)

    @TypeConverter
    fun fromMealStatus(value: MealStatus): String = value.name

    @TypeConverter
    fun toMealStatus(value: String): MealStatus = MealStatus.valueOf(value)

    @TypeConverter
    fun fromReminderCategory(value: ReminderCategory): String = value.name

    @TypeConverter
    fun toReminderCategory(value: String): ReminderCategory = ReminderCategory.valueOf(value)
}
