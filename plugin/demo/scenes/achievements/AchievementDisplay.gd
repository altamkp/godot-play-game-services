extends Control

@onready var id_label: Label = %IdLabel
@onready var name_label: Label = %NameLabel
@onready var description_label: Label = %DescriptionLabel
@onready var type_label: Label = %TypeLabel
@onready var state_label: Label = %StateLabel
@onready var xp_value_label: Label = %XPValueLabel

@onready var current_steps_holder: HBoxContainer = %CurrentStepsHolder
@onready var current_steps_label: Label = %CurrentStepsLabel
@onready var total_steps_holder: HBoxContainer = %TotalStepsHolder
@onready var total_steps_label: Label = %TotalStepsLabel

@onready var unlock_holder: VBoxContainer = %UnlockHolder
@onready var unlock_button: Button = %UnlockButton

var achievement: AchievementsClient.Achievement

var _waiting := false

func _ready() -> void:
	if achievement:
		_set_up_display()
		_set_up_button_pressed()
		_connect_signals()

func _set_up_display() -> void:
	id_label.text = achievement.achievement_id
	name_label.text = achievement.achievement_name
	description_label.text = achievement.description
	type_label.text = AchievementsClient.Type.keys()[achievement.type]
	state_label.text = AchievementsClient.State.keys()[achievement.state]
	xp_value_label.text = str(achievement.xp_value)
	
	if achievement.type == AchievementsClient.Type.TYPE_INCREMENTAL:
		current_steps_holder.visible = true
		current_steps_label.text = achievement.formatted_current_steps
		total_steps_holder.visible = true
		total_steps_label.text = achievement.formatted_total_steps
	
	match achievement.state:
		AchievementsClient.State.STATE_UNLOCKED:
			unlock_button.text = "Unlocked!"
			unlock_button.disabled = true
		AchievementsClient.State.STATE_HIDDEN:
			unlock_button.text = "Reveal!"
			unlock_button.disabled = false
		AchievementsClient.State.STATE_REVEALED:
			match achievement.type:
				AchievementsClient.Type.TYPE_INCREMENTAL:
					unlock_button.text = "Increment!"
					unlock_button.disabled = false
				AchievementsClient.Type.TYPE_STANDARD:
					unlock_button.text = "Unlock!"
					unlock_button.disabled = false

func _set_up_button_pressed() -> void:
	unlock_button.pressed.connect(func():
		match achievement.state:
			AchievementsClient.State.STATE_HIDDEN:
				AchievementsClient.reveal_achievement(achievement.achievement_id)
				_set_up_waiting()
			AchievementsClient.State.STATE_REVEALED:
				match achievement.type:
					AchievementsClient.Type.TYPE_INCREMENTAL:
						AchievementsClient.increment_achievement(
							achievement.achievement_id,
							1
						)
						_set_up_waiting()
					AchievementsClient.Type.TYPE_STANDARD:
						AchievementsClient.unlock_achievement(
							achievement.achievement_id
						)
						_set_up_waiting()
	)

func _connect_signals() -> void:
	AchievementsClient.achievement_revealed.connect(
		func refresh_achievement(_is_revealed: bool, achievement_id: String):
			if achievement_id ==achievement.achievement_id and _waiting:
				AchievementsClient.load_achievements(true)
	)
	AchievementsClient.achievement_unlocked.connect(
		func refresh_achievement(_is_unlocked: bool, achievement_id: String):
			if achievement_id == achievement.achievement_id and _waiting:
				AchievementsClient.load_achievements(true)
	)
	AchievementsClient.achievements_loaded.connect(
		func refresh_achievement(achievements: Array[AchievementsClient.Achievement]):
			for new_achievement: AchievementsClient.Achievement in achievements:
				if new_achievement.achievement_id == achievement.achievement_id \
				and _waiting:
					achievement = new_achievement
					_waiting = false
					_set_up_display()
	)

func _set_up_waiting() -> void:
	_waiting = true
	unlock_button.disabled = true
	unlock_button.text = "Wait..."