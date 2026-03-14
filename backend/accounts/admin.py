from django.contrib import admin
from .models import UserCustom, Profile

<<<<<<< HEAD
@admin.register(UserCustom)
class UserCustomAdmin(admin.ModelAdmin):
    list_display = ('username', 'email', 'created_at')
    search_fields = ('username', 'email')

@admin.register(Profile)
class ProfileAdmin(admin.ModelAdmin):
    list_display = ('user', 'age', 'fitness_goal')
    search_fields = ('user__username', 'fitness_goal')
=======
# Register your models here.

from .models import UserCustom

admin.site.register(UserCustom)
>>>>>>> 5ff3483 (Splash Screen added)
