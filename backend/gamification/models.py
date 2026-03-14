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
    
