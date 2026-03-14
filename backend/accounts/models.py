from django.db import models

# Create your models here.

class UserCustom(models.Model):
    id = models.UUIDField(primary_key=True)
    email = models.EmailField(unique=True)
    username = models.CharField(max_length=100)
    password_hash = models.CharField(max_length=255)
    created_at = models.DateTimeField(auto_now_add=True)

    def make_password_hash(self, password):
        import hashlib
        return hashlib.sha256(password.encode()).hexdigest()
    
    def validate_password(self, password):
        return self.password_hash == self.make_password_hash(password)
    
    def __str__(self):
        return self.username
    

class Profile(models.Model):
        user = models.OneToOneField(UserCustom, on_delete=models.CASCADE)
        age = models.IntegerField(null=True)
        height = models.FloatField(null=True)
        weight = models.FloatField(null=True)
        fitness_goal = models.CharField(max_length=100)

        def __str__(self):
            return f"{self.user.username}'s Profile"


