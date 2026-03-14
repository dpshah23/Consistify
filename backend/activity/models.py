from django.db import models

# Create your models here.

class DailyActivity(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    date = models.DateField()
    squats = models.IntegerField(default=0)
    pushups = models.IntegerField(default=0)
    steps = models.IntegerField(default=0)

    def __str__(self):
        return f"{self.user.username} - {self.date}"
    
