from django.db import models

# Create your models here.
class RewardItem(models.Model):
    name = models.CharField(max_length=100)
    description = models.TextField()
    coin_cost = models.IntegerField()
    stock = models.IntegerField()

    def __str__(self):
        return self.name
    
class Redemption(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    item = models.ForeignKey(RewardItem, on_delete=models.CASCADE)
    redeemed_at = models.DateTimeField(auto_now_add=True)
    status = models.CharField(max_length=50)

    def __str__(self):
        return f"{self.user.username} - {self.item.name}"