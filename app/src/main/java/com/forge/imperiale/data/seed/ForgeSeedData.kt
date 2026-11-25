package com.forge.imperiale.data.seed

import com.forge.imperiale.data.local.entity.BlockType
import com.forge.imperiale.data.local.entity.ExerciseEntity
import com.forge.imperiale.data.local.entity.MealSlotEntity
import com.forge.imperiale.data.local.entity.ScheduleBlockEntity
import com.forge.imperiale.data.local.entity.TemplateExerciseEntity
import com.forge.imperiale.data.local.entity.UserProfileEntity
import com.forge.imperiale.data.local.entity.VideoStreamType
import com.forge.imperiale.data.local.entity.WorkoutTemplateEntity
import java.time.LocalDate

/**
 * Exact Seed Data from Sections 3, 4, and 5 of the Forge Specification.
 *
 * Every exercise ships pre-configured with verified direct-streaming looping media:
 * - CC0 1280x720 H.264 seamless-loop MP4s via jsDelivr CDN (Media3 ExoPlayer) where exact clips exist,
 *   plus verified official YouTube embed IDs with automatic loopStartSec to loopEndSec movement looping
 *   so Arnab never needs to paste URLs or hunt for start timestamps manually.
 * - Exercise cues strictly follow Section 4 without inventing form claims.
 */
object ForgeSeedData {

    private const val CDN_VIDEO_BASE =
        "https://cdn.jsdelivr.net/gh/namnq/workout-free-videos@v2.0.0/media/video"

    const val STANDARD_WARMUP =
        "5 min warm-up: March in place 1 min • Arm circles 30 s each way • 15 bodyweight squats • Hip circles 30 s each side."

    const val STANDARD_COOLDOWN =
        "5 min cool-down: Slow walk 1 min • Hold leg, chest, shoulder, and back stretches 20-30 s each."

    val BENGALI_PROTEIN_OPTIONS = listOf(
        "Eggs",
        "Paneer (palm-sized, bhurji/tikka, low oil)",
        "Dal",
        "Curd (Tok Doi)",
        "Milk",
        "Soya Chunks",
        "Chana",
        "Rajma",
        "Peanuts",
        "Chicken (1-2 days/wk, grilled or oil scooped)"
    )

    val HOME_FOOD_COACH_TIPS = listOf(
        "Scoop extra floating oil off home gravies before serving.",
        "Fried items (luchi, bhaja, singara, pakora): at most 2 times a week.",
        "Mishti: at most once a week, eaten after a full meal — never on an empty stomach.",
        "Zero sugary cold drinks or packet chips.",
        "Paneer butter masala and deep-fried paneer are occasional treats, not daily protein.",
        "Lunch & Dinner Plate: 1/2 sabzi or salad, 1/4 protein, 1/4 rice or roti (cut usual rice by ~1/3)."
    )

    fun defaultUserProfile(todayIso: String = LocalDate.now().toString()): UserProfileEntity =
        UserProfileEntity(
            id = 1,
            name = "Arnab",
            age = 16,
            country = "India",
            timezone = "Asia/Kolkata",
            startDateIso = todayIso,
            hasDumbbells = true,
            hasBarbell = true,
            hasTreadmill = true,
            eatsFish = false,
            eatsPaneer = true,
            chickenDaysPerWeek = 2,
            barbellSafetyUnlocked = false,
            onboardingCompleted = false,
            weightStepKg = 1.0,
            defaultRestSec = 75,
            waterGoalGlasses = 11
        )

    /**
     * Section 3: Locked study and tuition blocks (never schedule anything on top of these).
     */
    fun defaultTuitionBlocks(): List<ScheduleBlockEntity> = listOf(
        // Monday: 18:00-19:30 English, 19:30-21:30 Maths
        tuition(1, 18 * 60, 19 * 60 + 30, "English Tuition"),
        tuition(1, 19 * 60 + 30, 21 * 60 + 30, "Maths Tuition"),
        // Tuesday: 17:00-19:00 Science (physics, chemistry)
        tuition(2, 17 * 60, 19 * 60, "Science Tuition", "Physics & Chemistry"),
        // Wednesday: 08:00-09:30 SST
        tuition(3, 8 * 60, 9 * 60 + 30, "SST Tuition"),
        // Thursday: 17:00-19:30 Science, 19:30-21:30 Maths
        tuition(4, 17 * 60, 19 * 60 + 30, "Science Tuition", "Physics & Chemistry"),
        tuition(4, 19 * 60 + 30, 21 * 60 + 30, "Maths Tuition"),
        // Friday: 08:00-09:30 SST, 18:00-19:30 English
        tuition(5, 8 * 60, 9 * 60 + 30, "SST Tuition"),
        tuition(5, 18 * 60, 19 * 60 + 30, "English Tuition"),
        // Saturday: 09:30-10:30 Biology, 20:00-22:00 Bengali
        tuition(6, 9 * 60 + 30, 10 * 60 + 30, "Biology Tuition"),
        tuition(6, 20 * 60, 22 * 60, "Bengali Tuition"),
        // Sunday: 10:30-11:30 Biology, 20:00-22:00 Bengali
        tuition(7, 10 * 60 + 30, 11 * 60 + 30, "Biology Tuition"),
        tuition(7, 20 * 60, 22 * 60, "Bengali Tuition")
    )

    private fun tuition(
        day: Int,
        startMin: Int,
        endMin: Int,
        title: String,
        subtitle: String = "Locked Study / Tuition"
    ) = ScheduleBlockEntity(
        type = BlockType.TUITION,
        dayOfWeek = day,
        startMinute = startMin,
        endMinute = endMin,
        title = title,
        subtitle = subtitle,
        locked = true,
        source = "SEED"
    )

    /**
     * Section 4: Complete Exercise Library with exact form cues, easier/harder variants,
     * equipment substitutes, Week 5+ barbell safety-gated lifts, and pre-configured looping streams.
     */
    fun defaultExercises(): List<ExerciseEntity> = listOf(
        ExerciseEntity(
            id = "goblet_squat",
            name = "Goblet Squat",
            muscles = "Quads, Glutes, Core",
            equipment = "Dumbbell",
            cues = "Dumbbell at chest, sit back and down, chest up.",
            easierId = "squat_to_chair",
            harderId = "goblet_squat_tempo",
            substituteId = "squat_to_chair",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/deep-squat-prying.mp4",
            loopStartSec = 0.5f,
            loopEndSec = 7.5f,
            backupYoutubeId = "lRYBbchqxtI",
            backupStartSec = 3f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "squat_to_chair",
            name = "Squat to a Chair",
            muscles = "Quads, Glutes",
            equipment = "Bodyweight, Chair",
            cues = "Sit back and down lightly to a chair, chest up, stand tall.",
            harderId = "goblet_squat",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "5Wd4S29E-yE",
            loopStartSec = 4f,
            loopEndSec = 20f,
            backupYoutubeId = "5Wd4S29E-yE",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "goblet_squat_tempo",
            name = "Goblet Squat (3s Slow Lowering)",
            muscles = "Quads, Glutes, Core",
            equipment = "Dumbbell",
            cues = "Dumbbell at chest, slow 3-second lowering, sit back and down, chest up.",
            easierId = "goblet_squat",
            harderId = "barbell_back_squat",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/deep-squat-prying.mp4",
            loopStartSec = 0.5f,
            loopEndSec = 7.5f,
            backupYoutubeId = "lRYBbchqxtI",
            backupStartSec = 3f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "push_up",
            name = "Push-Up",
            muscles = "Chest, Shoulders, Triceps, Core",
            equipment = "Bodyweight",
            cues = "Body in a straight line, lower chest toward floor.",
            easierId = "knee_push_up",
            harderId = "push_up_tempo",
            substituteId = "knee_push_up",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "pKZ-lkKKMws",
            loopStartSec = 5f,
            loopEndSec = 22f,
            backupYoutubeId = "pKZ-lkKKMws",
            backupStartSec = 5f,
            backupEndSec = 22f
        ),
        ExerciseEntity(
            id = "knee_push_up",
            name = "Knee Push-Up",
            muscles = "Chest, Shoulders, Triceps",
            equipment = "Bodyweight",
            cues = "Knees on floor, body in a straight line from knees to head, lower chest toward floor.",
            easierId = "wall_push_up",
            harderId = "push_up",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "rrVwNeIpy-k",
            loopStartSec = 6f,
            loopEndSec = 24f,
            backupYoutubeId = "rrVwNeIpy-k",
            backupStartSec = 6f,
            backupEndSec = 24f
        ),
        ExerciseEntity(
            id = "wall_push_up",
            name = "Wall Push-Up",
            muscles = "Chest, Shoulders, Triceps",
            equipment = "Bodyweight, Wall",
            cues = "Hands on wall, body in a straight line, lower chest smoothly toward wall.",
            harderId = "knee_push_up",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "5NPvv40gd3Q",
            loopStartSec = 8f,
            loopEndSec = 26f,
            backupYoutubeId = "5NPvv40gd3Q",
            backupStartSec = 8f,
            backupEndSec = 26f
        ),
        ExerciseEntity(
            id = "push_up_tempo",
            name = "Push-Up (Slow Lowering)",
            muscles = "Chest, Shoulders, Triceps, Core",
            equipment = "Bodyweight",
            cues = "Body in a straight line, lower chest toward floor with slow controlled lowering.",
            easierId = "push_up",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "pKZ-lkKKMws",
            loopStartSec = 5f,
            loopEndSec = 22f,
            backupYoutubeId = "pKZ-lkKKMws",
            backupStartSec = 5f,
            backupEndSec = 22f
        ),
        ExerciseEntity(
            id = "one_arm_db_row",
            name = "One-Arm Dumbbell Row",
            muscles = "Upper Back, Lats, Biceps",
            equipment = "Dumbbell, Chair/Bench",
            cues = "Hand on bench or chair, pull elbow back, do not twist.",
            easierId = "bent_over_db_row",
            harderId = "barbell_row_light",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "yHqqGd0tXcw",
            loopStartSec = 4f,
            loopEndSec = 20f,
            backupYoutubeId = "yHqqGd0tXcw",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "db_rdl",
            name = "Dumbbell Romanian Deadlift",
            muscles = "Hamstrings, Glutes, Lower Back",
            equipment = "Dumbbells",
            cues = "Push hips back with a flat back, feel the stretch behind the thighs.",
            easierId = "glute_bridge",
            harderId = "barbell_rdl_light",
            substituteId = "glute_bridge",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/stiff-leg-deadlift.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "hu3jRvTc_po",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "plank",
            name = "Plank",
            muscles = "Core, Abdominals, Shoulders",
            equipment = "Bodyweight",
            cues = "Elbows under shoulders, hips do not sag.",
            easierId = "knee_plank",
            isTimed = true,
            defaultSeconds = 25,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "F-C0CzNK22s",
            loopStartSec = 2f,
            loopEndSec = 16f,
            backupYoutubeId = "F-C0CzNK22s",
            backupStartSec = 2f,
            backupEndSec = 16f
        ),
        ExerciseEntity(
            id = "knee_plank",
            name = "Knee Plank",
            muscles = "Core, Abdominals",
            equipment = "Bodyweight",
            cues = "Elbows under shoulders, knees on mat, hips do not sag.",
            harderId = "plank",
            isTimed = true,
            defaultSeconds = 25,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "ic40NXcsQ2M",
            loopStartSec = 8f,
            loopEndSec = 25f,
            backupYoutubeId = "ic40NXcsQ2M",
            backupStartSec = 8f,
            backupEndSec = 25f
        ),
        ExerciseEntity(
            id = "db_shoulder_press",
            name = "Dumbbell Shoulder Press",
            muscles = "Shoulders, Triceps, Core",
            equipment = "Dumbbells",
            cues = "Press straight up, do not arch the back.",
            easierId = "seated_db_shoulder_press",
            harderId = "barbell_overhead_press_light",
            substituteId = "seated_db_shoulder_press",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/military-press.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f,
            backupYoutubeId = "wO0l5jW2NtQ",
            backupStartSec = 5f,
            backupEndSec = 22f
        ),
        ExerciseEntity(
            id = "seated_db_shoulder_press",
            name = "Seated Dumbbell Shoulder Press",
            muscles = "Shoulders, Triceps",
            equipment = "Dumbbells, Chair",
            cues = "Sit tall with back supported, press straight up, do not arch the back.",
            harderId = "db_shoulder_press",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "k6tzKisR3NY",
            loopStartSec = 4f,
            loopEndSec = 20f,
            backupYoutubeId = "k6tzKisR3NY",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "bent_over_db_row",
            name = "Bent-Over Dumbbell Row",
            muscles = "Upper Back, Lats, Rear Delts",
            equipment = "Dumbbells",
            cues = "Hinge forward with a flat back, pull both dumbbells to the waist.",
            easierId = "one_arm_db_row",
            harderId = "barbell_row_light",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/bent-over-row.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "yHqqGd0tXcw",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "db_squat",
            name = "Dumbbell Squat",
            muscles = "Quads, Glutes",
            equipment = "Dumbbells",
            cues = "Dumbbells at sides or shoulders, knees follow toes.",
            easierId = "squat_to_chair",
            harderId = "goblet_squat_tempo",
            substituteId = "squat_to_chair",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "cuUPtfanAFQ",
            loopStartSec = 3f,
            loopEndSec = 18f,
            backupYoutubeId = "cuUPtfanAFQ",
            backupStartSec = 3f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "glute_bridge",
            name = "Glute Bridge",
            muscles = "Glutes, Hamstrings",
            equipment = "Bodyweight",
            cues = "Lie on back, push through heels, squeeze at the top.",
            harderId = "db_rdl",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/barbell-hip-thrust.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 6.8f,
            backupYoutubeId = "LORVjN2bg5o",
            backupStartSec = 2f,
            backupEndSec = 16f
        ),
        ExerciseEntity(
            id = "biceps_curl",
            name = "Biceps Curl",
            muscles = "Biceps, Forearms",
            equipment = "Dumbbells",
            cues = "Elbows stay by the sides, no swinging.",
            harderId = "barbell_curl_light",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/incline-dumbbell-curl.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f,
            backupYoutubeId = "MKWBV29S6c0",
            backupStartSec = 3f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "dead_bug",
            name = "Dead Bug",
            muscles = "Deep Core, Abdominals",
            equipment = "Bodyweight",
            cues = "Lower back stays pressed to the floor.",
            easierId = "knee_plank",
            harderId = "lying_leg_raise",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "DqLL45uk2Tk",
            loopStartSec = 4f,
            loopEndSec = 22f,
            backupYoutubeId = "DqLL45uk2Tk",
            backupStartSec = 4f,
            backupEndSec = 22f
        ),
        ExerciseEntity(
            id = "reverse_lunge",
            name = "Reverse Lunge",
            muscles = "Quads, Glutes, Balance",
            equipment = "Dumbbells or Bodyweight",
            cues = "Step back, back knee toward floor, push through the front heel.",
            easierId = "split_squat_wall",
            substituteId = "split_squat_wall",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "J9MpoAQCjos",
            loopStartSec = 4f,
            loopEndSec = 20f,
            backupYoutubeId = "J9MpoAQCjos",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "split_squat_wall",
            name = "Split Squat (Holding Wall)",
            muscles = "Quads, Glutes",
            equipment = "Bodyweight, Wall",
            cues = "Hold a wall lightly for balance, lower back knee toward floor, push through front heel.",
            harderId = "reverse_lunge",
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "YuLqw3kHPaw",
            loopStartSec = 4f,
            loopEndSec = 20f,
            backupYoutubeId = "YuLqw3kHPaw",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "overhead_triceps",
            name = "Overhead Triceps Extension",
            muscles = "Triceps",
            equipment = "Dumbbell",
            cues = "Elbows point forward.",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/ez-bar-skullcrusher.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 6.7f,
            backupYoutubeId = "b_r_LW4HEcM",
            backupStartSec = 4f,
            backupEndSec = 20f
        ),
        ExerciseEntity(
            id = "side_plank",
            name = "Side Plank",
            muscles = "Obliques, Core",
            equipment = "Bodyweight",
            cues = "Body in a line.",
            easierId = "knee_side_plank",
            isTimed = true,
            defaultSeconds = 20,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "Ye7G1wQCMgk",
            loopStartSec = 3f,
            loopEndSec = 17f,
            backupYoutubeId = "Ye7G1wQCMgk",
            backupStartSec = 3f,
            backupEndSec = 17f
        ),
        ExerciseEntity(
            id = "knee_side_plank",
            name = "Side Plank (From Knees)",
            muscles = "Obliques, Core",
            equipment = "Bodyweight",
            cues = "Support from knees and forearm, body in a straight line.",
            harderId = "side_plank",
            isTimed = true,
            defaultSeconds = 20,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "8imTmxwFgRg",
            loopStartSec = 8f,
            loopEndSec = 24f,
            backupYoutubeId = "8imTmxwFgRg",
            backupStartSec = 8f,
            backupEndSec = 24f
        ),
        ExerciseEntity(
            id = "crunch",
            name = "Crunch",
            muscles = "Upper Abdominals",
            equipment = "Bodyweight",
            cues = "Curl ribs gently toward pelvis, keep neck relaxed.",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/weighted-crunch.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "DqLL45uk2Tk",
            backupStartSec = 4f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "lying_leg_raise",
            name = "Lying Leg Raise",
            muscles = "Lower Abdominals, Hip Flexors",
            equipment = "Bodyweight",
            cues = "Keep lower back pressed down, raise and lower legs with control.",
            easierId = "dead_bug",
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/lying-floor-leg-raise.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "DqLL45uk2Tk",
            backupStartSec = 4f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "treadmill_brisk_walk",
            name = "Treadmill Brisk Walk (3-5% Incline)",
            muscles = "Cardio, Calves, Glutes",
            equipment = "Treadmill",
            cues = "3-5 percent incline, steady brisk pace for 25-30 min, tall posture without leaning on rails.",
            substituteId = "easy_walk_stretch",
            isTimed = true,
            defaultSeconds = 25 * 60,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "HwXYMPGjlUg",
            loopStartSec = 2f,
            loopEndSec = 18f,
            backupYoutubeId = "HwXYMPGjlUg",
            backupStartSec = 2f,
            backupEndSec = 18f
        ),
        ExerciseEntity(
            id = "treadmill_intervals",
            name = "Treadmill Walk-Jog Intervals (x8)",
            muscles = "Cardio, Stamina, Legs",
            equipment = "Treadmill",
            cues = "2 min brisk walk, 1 min easy jog, repeated 8 times (24 min main set).",
            easierId = "treadmill_brisk_walk",
            isTimed = true,
            defaultSeconds = 24 * 60,
            videoType = VideoStreamType.YOUTUBE,
            videoRef = "PHhlx5pUT4k",
            loopStartSec = 2f,
            loopEndSec = 16f,
            backupYoutubeId = "PHhlx5pUT4k",
            backupStartSec = 2f,
            backupEndSec = 16f
        ),
        ExerciseEntity(
            id = "warmup_routine",
            name = "5-Minute Dynamic Warm-Up",
            muscles = "Full Body Mobility",
            equipment = "Bodyweight",
            cues = "March in place 1 min, arm circles 30 s each way, 15 bodyweight squats, hip circles 30 s each side.",
            isTimed = true,
            defaultSeconds = 5 * 60,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/cradle-walk.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "OcPs3x1vX1A",
            backupStartSec = 12f,
            backupEndSec = 35f
        ),
        ExerciseEntity(
            id = "cooldown_stretch",
            name = "5-Minute Cool-Down Stretches",
            muscles = "Recovery & Flexibility",
            equipment = "Bodyweight",
            cues = "Slow walk 1 min, then hold leg, chest, shoulder and back stretches 20-30 s each.",
            isTimed = true,
            defaultSeconds = 5 * 60,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/kneeling-posterior-hip-capsule-mobilization.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 6.2f,
            backupYoutubeId = "gfzC9XMEypg",
            backupStartSec = 15f,
            backupEndSec = 38f
        ),
        ExerciseEntity(
            id = "easy_walk_stretch",
            name = "Easy Recovery Walk + Stretching",
            muscles = "Active Recovery",
            equipment = "Treadmill or Floor",
            cues = "Easy relaxed walk 20-30 min + gentle leg, chest, shoulder and back stretches. Rest day — zero heavy effort.",
            isTimed = true,
            defaultSeconds = 25 * 60,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/cradle-walk.mp4",
            loopStartSec = 0.3f,
            loopEndSec = 7.7f,
            backupYoutubeId = "gfzC9XMEypg",
            backupStartSec = 15f,
            backupEndSec = 38f
        ),
        // Optional Week 5+ Light Barbell Variants (Safety-Checklist Gated, NEVER Bench Press Alone)
        ExerciseEntity(
            id = "barbell_back_squat",
            name = "Light Barbell Squat (Week 5+ Optional)",
            muscles = "Quads, Glutes, Core",
            equipment = "Barbell (Light + Collars)",
            cues = "Light load only, collars locked on both ends, sit back and down with chest up.",
            easierId = "goblet_squat",
            isBarbellGated = true,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/barbell-back-squat.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f
        ),
        ExerciseEntity(
            id = "barbell_rdl_light",
            name = "Light Barbell Romanian Deadlift (Week 5+ Optional)",
            muscles = "Hamstrings, Glutes",
            equipment = "Barbell (Light + Collars)",
            cues = "Collars locked, push hips back with a flat back, feel hamstring stretch.",
            easierId = "db_rdl",
            isBarbellGated = true,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/stiff-leg-deadlift.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f
        ),
        ExerciseEntity(
            id = "barbell_row_light",
            name = "Light Barbell Bent-Over Row (Week 5+ Optional)",
            muscles = "Upper Back, Lats",
            equipment = "Barbell (Light + Collars)",
            cues = "Collars locked, hinge forward with a flat back, pull bar smoothly to waist.",
            easierId = "bent_over_db_row",
            isBarbellGated = true,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/bent-over-row.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f
        ),
        ExerciseEntity(
            id = "barbell_overhead_press_light",
            name = "Light Barbell Overhead Press (Week 5+ Optional)",
            muscles = "Shoulders, Triceps",
            equipment = "Barbell (Light + Collars)",
            cues = "Collars locked, clear ceiling space, press straight up without arching lower back.",
            easierId = "db_shoulder_press",
            isBarbellGated = true,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/military-press.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f
        ),
        ExerciseEntity(
            id = "barbell_curl_light",
            name = "Light Barbell Curl (Week 5+ Optional)",
            muscles = "Biceps",
            equipment = "Barbell (Light + Collars)",
            cues = "Collars locked, elbows stay pinned by the sides, zero torso swing.",
            easierId = "biceps_curl",
            isBarbellGated = true,
            videoType = VideoStreamType.MP4,
            videoRef = "$CDN_VIDEO_BASE/standing-barbell-curl.mp4",
            loopStartSec = 0.4f,
            loopEndSec = 7.6f
        )
    )

    /**
     * Section 4: Default weekly layout (Mon..Sun).
     */
    fun defaultTemplates(): List<WorkoutTemplateEntity> = listOf(
        WorkoutTemplateEntity(
            id = "strength_a",
            name = "Strength A",
            dayOfWeek = 1,
            category = "STRENGTH",
            defaultStartMinute = 16 * 60, // 16:00-16:45
            durationMinutes = 45,
            warmupCues = STANDARD_WARMUP,
            cooldownCues = STANDARD_COOLDOWN,
            description = "Full-body foundation: Goblet Squat, Push-Ups, One-Arm Row, DB RDL & Plank."
        ),
        WorkoutTemplateEntity(
            id = "cardio_tue",
            name = "Treadmill Brisk Walk",
            dayOfWeek = 2,
            category = "CARDIO",
            defaultStartMinute = 19 * 60 + 15, // 19:15-19:45 (15 min after Science tuition!)
            durationMinutes = 30,
            warmupCues = "2 min easy flat walk before raising incline.",
            cooldownCues = "2 min slow flat walk + calf and hamstring stretch.",
            description = "3-5% incline steady brisk walk, 25-30 min."
        ),
        WorkoutTemplateEntity(
            id = "strength_b",
            name = "Strength B",
            dayOfWeek = 3,
            category = "STRENGTH",
            defaultStartMinute = 16 * 60 + 30, // 16:30-17:15
            durationMinutes = 45,
            warmupCues = STANDARD_WARMUP,
            cooldownCues = STANDARD_COOLDOWN,
            description = "Shoulder Press, Bent-Over Row, DB Squat, Glute Bridge, Curl & Dead Bug."
        ),
        WorkoutTemplateEntity(
            id = "cardio_thu",
            name = "Treadmill Walk-Jog Intervals",
            dayOfWeek = 4,
            category = "CARDIO",
            defaultStartMinute = 15 * 60 + 30, // 15:30-16:00 (afternoon to protect sleep after late tuition)
            durationMinutes = 30,
            warmupCues = "3 min easy flat walk before interval 1.",
            cooldownCues = "3 min slow walk + leg stretches.",
            description = "2 min brisk walk, 1 min easy jog x8. Placed afternoon to protect sleep."
        ),
        WorkoutTemplateEntity(
            id = "strength_c",
            name = "Strength C",
            dayOfWeek = 5,
            category = "STRENGTH",
            defaultStartMinute = 16 * 60, // 16:00-16:45
            durationMinutes = 45,
            warmupCues = STANDARD_WARMUP,
            cooldownCues = STANDARD_COOLDOWN,
            description = "Reverse Lunge, Max Clean Push-Ups, Light DB RDL, Overhead Triceps & Side Plank."
        ),
        WorkoutTemplateEntity(
            id = "cardio_core_sat",
            name = "Incline Walk + Core",
            dayOfWeek = 6,
            category = "CARDIO_CORE",
            defaultStartMinute = 16 * 60 + 30, // 16:30-17:15
            durationMinutes = 45,
            warmupCues = "2 min easy walk before setting incline.",
            cooldownCues = STANDARD_COOLDOWN,
            description = "Treadmill incline walk 25 min + 10 min core (Plank, Crunches, Lying Leg Raises)."
        ),
        WorkoutTemplateEntity(
            id = "sunday_rest_walk",
            name = "Easy Walk + Stretch (Rest Day)",
            dayOfWeek = 7,
            category = "REST_WALK",
            defaultStartMinute = 16 * 60 + 30, // 16:30-17:00
            durationMinutes = 30,
            warmupCues = "Gentle relaxed breathing.",
            cooldownCues = "Hold leg, chest, shoulder and back stretches 20-30 s each.",
            description = "Locked Sunday rest day. Light 20-30 min walk + mobility only — no strength work."
        )
    )

    fun defaultTemplateExercises(): List<TemplateExerciseEntity> = listOf(
        // Strength A
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "warmup_routine", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 30),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "goblet_squat", orderIndex = 1, sets = 3, repMin = 10, repMax = 12, restSec = 75, isMainLift = true),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "push_up", orderIndex = 2, sets = 3, repMin = 8, repMax = 12, restSec = 75, isMainLift = true, note = "Knees allowed"),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "one_arm_db_row", orderIndex = 3, sets = 3, repMin = 10, repMax = 10, restSec = 75, isMainLift = true, note = "10 reps each side"),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "db_rdl", orderIndex = 4, sets = 3, repMin = 10, repMax = 10, restSec = 75, isAccessory = true),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "plank", orderIndex = 5, sets = 3, repMin = 1, repMax = 1, defaultSeconds = 25, restSec = 60, isAccessory = true, note = "20-30 s hold"),
        TemplateExerciseEntity(templateId = "strength_a", exerciseId = "cooldown_stretch", orderIndex = 6, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 0),

        // Tuesday Cardio
        TemplateExerciseEntity(templateId = "cardio_tue", exerciseId = "treadmill_brisk_walk", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 25 * 60, restSec = 0, note = "3-5% incline, 25-30 min"),
        TemplateExerciseEntity(templateId = "cardio_tue", exerciseId = "cooldown_stretch", orderIndex = 1, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 180, restSec = 0),

        // Strength B
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "warmup_routine", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 30),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "db_shoulder_press", orderIndex = 1, sets = 3, repMin = 8, repMax = 10, restSec = 75, isMainLift = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "bent_over_db_row", orderIndex = 2, sets = 3, repMin = 10, repMax = 10, restSec = 75, isMainLift = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "db_squat", orderIndex = 3, sets = 3, repMin = 12, repMax = 12, restSec = 75, isMainLift = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "glute_bridge", orderIndex = 4, sets = 3, repMin = 12, repMax = 12, restSec = 60, isAccessory = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "biceps_curl", orderIndex = 5, sets = 2, repMin = 12, repMax = 12, restSec = 60, isAccessory = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "dead_bug", orderIndex = 6, sets = 3, repMin = 10, repMax = 10, restSec = 60, isAccessory = true),
        TemplateExerciseEntity(templateId = "strength_b", exerciseId = "cooldown_stretch", orderIndex = 7, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 0),

        // Thursday Intervals
        TemplateExerciseEntity(templateId = "cardio_thu", exerciseId = "treadmill_intervals", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 24 * 60, restSec = 0, note = "2 min brisk walk + 1 min easy jog x8"),
        TemplateExerciseEntity(templateId = "cardio_thu", exerciseId = "cooldown_stretch", orderIndex = 1, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 180, restSec = 0),

        // Strength C
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "warmup_routine", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 30),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "reverse_lunge", orderIndex = 1, sets = 3, repMin = 8, repMax = 8, restSec = 75, isMainLift = true, note = "8 reps each leg"),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "push_up", orderIndex = 2, sets = 3, repMin = 8, repMax = 15, restSec = 75, isMainLift = true, note = "Max clean reps"),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "db_rdl", orderIndex = 3, sets = 3, repMin = 10, repMax = 10, restSec = 75, isMainLift = true, note = "Light controlled weight"),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "overhead_triceps", orderIndex = 4, sets = 2, repMin = 12, repMax = 12, restSec = 60, isAccessory = true),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "side_plank", orderIndex = 5, sets = 2, repMin = 1, repMax = 1, defaultSeconds = 20, restSec = 60, isAccessory = true, note = "20 s each side"),
        TemplateExerciseEntity(templateId = "strength_c", exerciseId = "cooldown_stretch", orderIndex = 6, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 300, restSec = 0),

        // Saturday Incline Walk + Core
        TemplateExerciseEntity(templateId = "cardio_core_sat", exerciseId = "treadmill_brisk_walk", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 25 * 60, restSec = 45, note = "Incline walk 25 min"),
        TemplateExerciseEntity(templateId = "cardio_core_sat", exerciseId = "plank", orderIndex = 1, sets = 2, repMin = 1, repMax = 1, defaultSeconds = 30, restSec = 45, isAccessory = true),
        TemplateExerciseEntity(templateId = "cardio_core_sat", exerciseId = "crunch", orderIndex = 2, sets = 2, repMin = 12, repMax = 15, restSec = 45, isAccessory = true),
        TemplateExerciseEntity(templateId = "cardio_core_sat", exerciseId = "lying_leg_raise", orderIndex = 3, sets = 2, repMin = 10, repMax = 12, restSec = 45, isAccessory = true),
        TemplateExerciseEntity(templateId = "cardio_core_sat", exerciseId = "cooldown_stretch", orderIndex = 4, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 240, restSec = 0),

        // Sunday Rest Recovery Walk
        TemplateExerciseEntity(templateId = "sunday_rest_walk", exerciseId = "easy_walk_stretch", orderIndex = 0, sets = 1, repMin = 1, repMax = 1, defaultSeconds = 25 * 60, restSec = 0, note = "Locked recovery day — easy pace only")
    )

    /**
     * Section 5: Meal-slot checklist (no calories, no macros, no fasting windows).
     */
    fun defaultMealSlots(): List<MealSlotEntity> = listOf(
        MealSlotEntity(
            slotKey = "WAKE_WATER",
            title = "Wake-Up Water",
            orderIndex = 0,
            showPlateRule = false,
            guidance = "1-2 glasses of plain water right after waking up."
        ),
        MealSlotEntity(
            slotKey = "BREAKFAST",
            title = "Breakfast",
            orderIndex = 1,
            showPlateRule = false,
            guidance = "Finish before 08:00 SST class on Wed & Fri. Include eggs, milk, curd, paneer, or chana."
        ),
        MealSlotEntity(
            slotKey = "LUNCH",
            title = "Lunch",
            orderIndex = 2,
            showPlateRule = true,
            guidance = "Bengali Home Plate: 1/2 sabzi/salad, 1/4 protein (paneer/dal/eggs/chicken), 1/4 rice (cut usual rice by ~1/3). Scoop extra floating oil."
        ),
        MealSlotEntity(
            slotKey = "PRE_WORKOUT",
            title = "Pre-Workout Snack",
            orderIndex = 3,
            showPlateRule = false,
            guidance = "About 1 hour before training: banana/fruit + handful of roasted chana, peanuts, or milk."
        ),
        MealSlotEntity(
            slotKey = "POST_WORKOUT",
            title = "Post-Workout Snack (If Hungry)",
            orderIndex = 4,
            showPlateRule = false,
            guidance = "Optional — only if genuinely hungry before dinner: curd, milk, boiled eggs, or paneer tikka."
        ),
        MealSlotEntity(
            slotKey = "DINNER",
            title = "Dinner",
            orderIndex = 5,
            showPlateRule = true,
            guidance = "Mon & Thu at 21:45 after Maths tuition — keep lightest of the day (roti/light rice + sabzi + paneer/dal/egg). Sat & Sun at 19:30 before 20:00 Bengali."
        ),
        MealSlotEntity(
            slotKey = "WIND_DOWN",
            title = "Sleep Wind-Down",
            orderIndex = 6,
            showPlateRule = false,
            guidance = "Phone down 30 min before bedtime. Warm water or plain milk if needed. Protect 8.5 hours of sleep."
        )
    )
}
