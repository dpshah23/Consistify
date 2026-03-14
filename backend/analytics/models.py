from django.db import models

# Create your models here.

class Leaderboard(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    xp = models.IntegerField()
    rank = models.IntegerField()
    week = models.DateField()

    def __str__(self):
        return f"{self.user.username} - Rank {self.rank} - {self.xp} XP"
    