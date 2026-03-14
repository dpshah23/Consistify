import datetime
from django.http import JsonResponse
from django.utils import timezone
from accounts.models import UserCustom
from activity.models import DailyActivity
from gamification.models import UserStats, Streak
from .models import Leaderboard
from django.views.decorators.csrf import csrf_exempt

@csrf_exempt
def get_dashboard_summary(request):
    """
    Unified API for Android home page dashboard.
    Returns:
    - Today's activity
    - Gamification stats (XP, level, FitCoins, consistency)
    - Current streak
    """
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        
        if not user_id:
            return JsonResponse({"error": "user_id is required"}, status=400)
            
        try:
            user = UserCustom.objects.get(id=user_id)
            today = timezone.now().date()
            
            # 1. Today's Activity
            today_act = DailyActivity.objects.filter(user=user, date=today).first()
            activity_data = {
                "squats": today_act.squats if today_act else 0,
                "pushups": today_act.pushups if today_act else 0,
                "steps": today_act.steps if today_act else 0,
            }
            
            # 2. Gamification Stats (Level, XP, Coins, Consistency)
            # Create if it doesn't exist
            stats, _ = UserStats.objects.get_or_create(user=user)
            stats_data = {
                "xp": stats.xp,
                "fitcoins": stats.fitcoins,
                "level": stats.level,
                "consistency_score": stats.consistency_score
            }
            
            # 3. Streak
            streak, _ = Streak.objects.get_or_create(
                user=user, 
                defaults={"last_active_date": today - datetime.timedelta(days=1)} # fallback to yesterday
            )
            streak_data = {
                "current_streak": streak.current_streak,
                "longest_streak": streak.longest_streak,
                "last_active_date": streak.last_active_date
            }
            
            # Next level threshold logic (Tortoise -> Wolf -> Eagle -> Leopard -> Lion)
            level_thresholds = {
                "Tortoise": 100,
                "Wolf": 300,
                "Eagle": 600,
                "Leopard": 1000,
                "Lion": 999999
            }
            next_xp = level_thresholds.get(stats.level, 100)
            progress_percent = min(100, int((stats.xp / next_xp) * 100)) if stats.xp < next_xp else 100

            return JsonResponse({
                "activity": activity_data,
                "stats": stats_data,
                "streak": streak_data,
                "next_level_xp": next_xp,
                "progress_percent": progress_percent
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)

@csrf_exempt
def get_weekly_chart_data(request):
    """
    Returns XP or Activity data for the past 7 days 
    for Android Bar Charts/Line Charts.
    """
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        
        if not user_id:
            return JsonResponse({"error": "user_id is required"}, status=400)
            
        try:
            user = UserCustom.objects.get(id=user_id)
            today = timezone.now().date()
            last_7_days = [today - datetime.timedelta(days=i) for i in range(6, -1, -1)]
            
            activities = DailyActivity.objects.filter(user=user, date__in=last_7_days)
            activity_map = {act.date: act for act in activities}
            
            chart_data = []
            for d in last_7_days:
                act = activity_map.get(d)
                chart_data.append({
                    "date": d.strftime("%Y-%m-%d"),
                    "day_name": d.strftime("%a"), # e.g., 'Mon'
                    "squats": act.squats if act else 0,
                    "pushups": act.pushups if act else 0,
                    "steps": act.steps if act else 0,
                    "completed": bool(act and (act.squats > 0 or act.pushups > 0 or act.steps > 0))
                })
                
            return JsonResponse({"weekly_data": chart_data})
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)


from django.db.models import Sum
from gamification.models import XPLog

@csrf_exempt
def get_leaderboard(request):
    """
    Returns top users sorted by XP or Consistency Score.
    Supports ?timeframe=daily | weekly | all_time
    """
    if request.method == "GET":
        timeframe = request.GET.get("timeframe", "all_time")
        today = timezone.now().date()
        leaderboard_data = []

        if timeframe == "daily":
            # Using __gte logic makes it more robust with timestamp entries
            start_of_day = timezone.now().replace(hour=0, minute=0, second=0, microsecond=0)
            logs = XPLog.objects.filter(created_at__gte=start_of_day)
            
            user_xp = logs.values('user__id', 'user__username').annotate(
                total_xp=Sum('xp_amount')
            ).order_by('-total_xp')[:10]
            
            for index, item in enumerate(user_xp):
                leaderboard_data.append({
                    "rank": index + 1,
                    "username": item['user__username'],
                    "xp": item['total_xp'],
                    "level": "Unranked",
                    "consistency": 0,
                    "user_id": str(item['user__id'])
                })

        elif timeframe == "weekly":
            start_of_week = today - datetime.timedelta(days=7)
            logs = XPLog.objects.filter(created_at__date__gte=start_of_week)
            
            user_xp = logs.values('user__id', 'user__username').annotate(
                total_xp=Sum('xp_amount')
            ).order_by('-total_xp')[:10]
            
            for index, item in enumerate(user_xp):
                leaderboard_data.append({
                    "rank": index + 1,
                    "username": item['user__username'],
                    "xp": item['total_xp'],
                    "level": "Unranked",
                    "consistency": 0,
                    "user_id": str(item['user__id'])
                })

        else:
            # All-time relies on UserStats which caches the total
            top_users = UserStats.objects.select_related('user').order_by('-xp')[:10]
            for index, stats in enumerate(top_users):
                leaderboard_data.append({
                    "rank": index + 1,
                    "username": stats.user.username,
                    "xp": stats.xp,
                    "level": stats.level,
                    "consistency": stats.consistency_score,
                    "user_id": str(stats.user.id)
                })
                
        return JsonResponse({"leaderboard": leaderboard_data})
