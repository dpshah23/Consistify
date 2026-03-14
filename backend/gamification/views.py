import datetime
from django.utils import timezone
from django.http import JsonResponse
from accounts.models import UserCustom
from .models import UserStats, Streak, XPLog

# Constants from gamification logic
LEVEL_THRESHOLDS = {
    "Tortoise": 0,
    "Wolf": 100,
    "Eagle": 300,
    "Leopard": 600,
    "Lion": 1000
}

XP_PER_SQUAT = 1  # 10 squats = 10 XP
COIN_PER_SQUAT = 0.5  # 10 squats = 5 coins

XP_PER_PUSHUP = 1.2  # 10 pushups = 12 XP
COIN_PER_PUSHUP = 0.6  # 10 pushups = 6 coins

XP_PER_STEP = 0.005  # 1000 steps = 5 XP
COIN_PER_STEP = 0.003  # 1000 steps = 3 coins

DAILY_XP_CAP = 150

def determine_level(xp):
    if xp >= 1000:
        return "Lion"
    elif xp >= 600:
        return "Leopard"
    elif xp >= 300:
        return "Eagle"
    elif xp >= 100:
        return "Wolf"
    else:
        return "Tortoise"

def calculate_consistency_score(streak_days, completion_rate, steps):
    # Example Formula: 0.5 * streak + 0.3 * completion + 0.2 * (steps/1000)
    # Using a simplified version based on provided specs
    score = (0.5 * streak_days) + (0.3 * completion_rate) + (0.2 * (steps / 100))
    return int(score)


def process_daily_gamification(request):
    """
    POST API to process daily gamification loop.
    Expects JSON or POST data: user_id, squats, pushups, steps
    """
    if request.method == "POST":
        user_id = request.POST.get("user_id")
        squats = int(request.POST.get("squats", 0))
        pushups = int(request.POST.get("pushups", 0))
        steps = int(request.POST.get("steps", 0))
        
        try:
            user = UserCustom.objects.get(id=user_id)
            stats, _ = UserStats.objects.get_or_create(user=user)
            streak, _ = Streak.objects.get_or_create(
                user=user,
                defaults={"last_active_date": timezone.now().date() - datetime.timedelta(days=1)}
            )
            
            # --- 1. Streak Logic ---
            today = timezone.now().date()
            yesterday = today - datetime.timedelta(days=1)
            
            streak_bonus_xp = 0
            streak_bonus_coins = 0
            
            if streak.last_active_date == yesterday:
                streak.current_streak += 1
                
                # Check for milestone bonuses
                if streak.current_streak == 3:
                    streak_bonus_xp = 10
                    streak_bonus_coins = 5
                elif streak.current_streak == 7:
                    streak_bonus_xp = 30
                    streak_bonus_coins = 10
                elif streak.current_streak == 14:
                    streak_bonus_xp = 70
                    streak_bonus_coins = 20
                elif streak.current_streak == 30:
                    streak_bonus_xp = 150
                    streak_bonus_coins = 50
                    
            elif streak.last_active_date == today:
                # Already logged today, don't increment streak
                pass 
            else:
                # Comeback system / Streak broken
                # Example: If they did 30 squats today, restore 70% streak
                if squats >= 30 and streak.current_streak > 0:
                    streak.current_streak = int(streak.current_streak * 0.7)
                else:
                    streak.current_streak = 1 # Reset to 1 for today
                    
            streak.last_active_date = today
            if streak.current_streak > streak.longest_streak:
                streak.longest_streak = streak.current_streak
            streak.save()
            
            # --- 2. Calculate XP and Coins ---
            earned_xp = int((squats * XP_PER_SQUAT) + (pushups * XP_PER_PUSHUP) + (steps * XP_PER_STEP))
            earned_coins = int((squats * COIN_PER_SQUAT) + (pushups * COIN_PER_PUSHUP) + (steps * COIN_PER_STEP))
            
            # Anti-Cheat: Daily Cap
            if earned_xp > DAILY_XP_CAP:
                earned_xp = DAILY_XP_CAP
                earned_coins = int(DAILY_XP_CAP / 2) # Rough cap on coins proportional to XP
                
            total_earned_xp = earned_xp + streak_bonus_xp
            total_earned_coins = earned_coins + streak_bonus_coins

            # --- 3. Update User Stats ---
            stats.xp += total_earned_xp
            stats.fitcoins += total_earned_coins
            
            old_level = stats.level
            stats.level = determine_level(stats.xp)
            
            # Dummy completion rate (can be fetched from DailyActivity) for consistency score
            completion_rate = 80 if (squats > 0 or pushups > 0 or steps > 5000) else 0 
            stats.consistency_score = calculate_consistency_score(streak.current_streak, completion_rate, steps)

            stats.save()
            
            # Log XP 
            XPLog.objects.create(user=user, source="daily_workout", xp_amount=earned_xp)
            if streak_bonus_xp > 0:
                XPLog.objects.create(user=user, source="streak_bonus", xp_amount=streak_bonus_xp)

            return JsonResponse({
                "message": "Gamification processed successfully",
                "earned_xp": total_earned_xp,
                "earned_coins": total_earned_coins,
                "new_level": stats.level,
                "level_up": old_level != stats.level,
                "current_streak": streak.current_streak,
                "consistency_score": stats.consistency_score
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
    
    return JsonResponse({"error": "Invalid request method"}, status=400)


def fetch_user_gamification(request):
    """
    GET API to get user's current gamification state
    """
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        try:
            user = UserCustom.objects.get(id=user_id)
            stats, _ = UserStats.objects.get_or_create(user=user)
            streak, _ = Streak.objects.get_or_create(
                user=user,
                defaults={"last_active_date": timezone.now().date() - datetime.timedelta(days=1)}
            )
            
            return JsonResponse({
                "xp": stats.xp,
                "fitcoins": stats.fitcoins,
                "level": stats.level,
                "consistency_score": stats.consistency_score,
                "current_streak": streak.current_streak,
                "longest_streak": streak.longest_streak
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
