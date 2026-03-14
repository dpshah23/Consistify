from django.db import models

# Create your models here.

class UserStats(models.Model):
    user = models.OneToOneField("accounts.UserCustom", on_delete=models.CASCADE)
    xp = models.IntegerField(default=0)
    fitcoins = models.IntegerField(default=0)
    level = models.CharField(max_length=50, default="Tortoise")
    consistency_score = models.IntegerField(default=0)

    def __str__(self):
        return f"{self.user.username} Stats"
    
class Streak(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    current_streak = models.IntegerField(default=0)
    longest_streak = models.IntegerField(default=0)
    last_active_date = models.DateField()

    def __str__(self):
        return f"{self.user.username} Streak"
    
class XPLog(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    source = models.CharField(max_length=50)
    xp_amount = models.IntegerField()
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user.username} - {self.source} - {self.xp_amount} XP"
    
class Challenge(models.Model):
    EXERCISE_CHOICES = [
        ('squats', 'Squats'),
        ('pushups', 'Pushups'),
    ]
    STATUS_CHOICES = [
        ('pending', 'Pending'),
        ('accepted', 'Accepted'),
        ('declined', 'Declined'),
        ('completed', 'Completed'),
    ]
    
    challenger = models.ForeignKey("accounts.UserCustom", related_name="challenges_sent", on_delete=models.CASCADE)
    challenged = models.ForeignKey("accounts.UserCustom", related_name="challenges_received", on_delete=models.CASCADE)
    exercise_type = models.CharField(max_length=20, choices=EXERCISE_CHOICES)
    status = models.CharField(max_length=20, choices=STATUS_CHOICES, default='pending')
    
    challenger_score = models.IntegerField(default=0, null=True, blank=True)
    challenged_score = models.IntegerField(default=0, null=True, blank=True)
    
    challenger_completed = models.BooleanField(default=False)
    challenged_completed = models.BooleanField(default=False)
    
    winner = models.ForeignKey("accounts.UserCustom", related_name="challenges_won", on_delete=models.SET_NULL, null=True, blank=True)
    
    created_at = models.DateTimeField(auto_now_add=True)
    accepted_at = models.DateTimeField(null=True, blank=True)
    completed_at = models.DateTimeField(null=True, blank=True)

    def __str__(self):
        return f"Challenge: {self.challenger.username} vs {self.challenged.username} - {self.exercise_type}"

class Notification(models.Model):
    user = models.ForeignKey("accounts.UserCustom", related_name="notifications", on_delete=models.CASCADE)
    message = models.CharField(max_length=255)
    is_read = models.BooleanField(default=False)
    created_at = models.DateTimeField(auto_now_add=True)
    
    def __str__(self):
        return f"Notification for {self.user.username}: {self.message}"
