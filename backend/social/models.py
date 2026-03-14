from django.db import models

# Create your models here.
class Post(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    content = models.TextField()
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user.username} - {self.created_at}"
    

class Like(models.Model):
    user = models.ForeignKey("accounts.UserCustom", on_delete=models.CASCADE)
    post = models.ForeignKey(Post, on_delete=models.CASCADE)
    created_at = models.DateTimeField(auto_now_add=True)

    def __str__(self):
        return f"{self.user.username} liked Post {self.post.id}"
    
