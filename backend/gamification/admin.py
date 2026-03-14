from django.contrib import admin
from .models import UserStats, Streak, XPLog

# Register your models here.
admin.site.register(UserStats)
admin.site.register(Streak)
admin.site.register(XPLog)
