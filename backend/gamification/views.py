import datetime
from django.utils import timezone
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
import json
from django.db import models
from accounts.models import UserCustom
from activity.models import DailyActivity
from .models import UserStats, Streak, XPLog, Challenge, Notification

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


@csrf_exempt
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
            
            # Fetch today's activity to calculate delta
            today = timezone.now().date()
            yesterday = today - datetime.timedelta(days=1)
            
            daily_act, _ = DailyActivity.objects.get_or_create(user=user, date=today)
            
            new_squats = max(0, squats - daily_act.squats)
            new_pushups = max(0, pushups - daily_act.pushups)
            new_steps = max(0, steps - daily_act.steps)
            
            # Update DailyActivity
            daily_act.squats = max(daily_act.squats, squats)
            daily_act.pushups = max(daily_act.pushups, pushups)
            daily_act.steps = max(daily_act.steps, steps)
            daily_act.save()
            
            # --- 1. Streak Logic ---
            
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
            earned_xp = int((new_squats * XP_PER_SQUAT) + (new_pushups * XP_PER_PUSHUP) + (new_steps * XP_PER_STEP))
            earned_coins = int((new_squats * COIN_PER_SQUAT) + (new_pushups * COIN_PER_PUSHUP) + (new_steps * COIN_PER_STEP))
            
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
            
            # Log XP only if earned > 0
            if earned_xp > 0:
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


@csrf_exempt
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
            
            recent_logs = XPLog.objects.filter(user=user).order_by("-created_at")[:5]
            recent_achievements = [f"{log.source.replace('_', ' ').title()} (+{log.xp_amount} XP)" for log in recent_logs]

            return JsonResponse({
                "xp": stats.xp,
                "fitcoins": stats.fitcoins,
                "level": stats.level,
                "consistency_score": stats.consistency_score,
                "current_streak": streak.current_streak,
                "longest_streak": streak.longest_streak,
                "recent_achievements": recent_achievements
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)

@csrf_exempt
def send_challenge(request):
    if request.method == "POST":
        data = json.loads(request.body) if request.content_type == 'application/json' else request.POST
        challenger_id = data.get("challenger_id")
        challenged_id = data.get("challenged_id")
        exercise_type = data.get("exercise_type") # 'squats' or 'pushups'
        
        try:
            challenger = UserCustom.objects.get(id=challenger_id)
            challenged = UserCustom.objects.get(id=challenged_id)
            
            challenge = Challenge.objects.create(
                challenger=challenger,
                challenged=challenged,
                exercise_type=exercise_type
            )
            
            Notification.objects.create(
                user=challenged,
                message=f"{challenger.username} has challenged you for a blitz of {exercise_type}!"
            )
            
            return JsonResponse({"message": "Challenge sent successfully", "challenge_id": str(challenge.id)})
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)

@csrf_exempt
def respond_challenge(request):
    if request.method == "POST":
        data = json.loads(request.body) if request.content_type == 'application/json' else request.POST
        challenge_id = data.get("challenge_id")
        action = data.get("action") # 'accept' or 'decline'
        
        try:
            challenge = Challenge.objects.get(id=challenge_id)
            if action == 'accept':
                challenge.status = 'accepted'
                challenge.accepted_at = timezone.now()
                challenge.save()
                
                Notification.objects.create(
                    user=challenge.challenger,
                    message=f"{challenge.challenged.username} has accepted your {challenge.exercise_type} challenge! Let the blitz begin."
                )
                return JsonResponse({"message": "Challenge accepted"})
            elif action == 'decline':
                challenge.status = 'declined'
                challenge.save()
                
                Notification.objects.create(
                    user=challenge.challenger,
                    message=f"{challenge.challenged.username} declined your {challenge.exercise_type} challenge."
                )
                return JsonResponse({"message": "Challenge declined"})
            else:
                return JsonResponse({"error": "Invalid action"}, status=400)
        except Challenge.DoesNotExist:
            return JsonResponse({"error": "Challenge not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)

@csrf_exempt
def submit_challenge_score(request):
    if request.method == "POST":
        data = json.loads(request.body) if request.content_type == 'application/json' else request.POST
        challenge_id = data.get("challenge_id")
        user_id = data.get("user_id")
        score = int(data.get("score", 0))
        
        try:
            challenge = Challenge.objects.get(id=challenge_id)
            user = UserCustom.objects.get(id=user_id)
            
            if challenge.status != 'accepted':
                return JsonResponse({"error": "Challenge is not ongoing"}, status=400)
                
            if str(challenge.challenger.id) == str(user.id):
                challenge.challenger_score = score
                challenge.challenger_completed = True
            elif str(challenge.challenged.id) == str(user.id):
                challenge.challenged_score = score
                challenge.challenged_completed = True
            else:
                return JsonResponse({"error": "User not part of this challenge"}, status=400)
                
            challenge.save()
            
            if challenge.challenger_completed and challenge.challenged_completed:
                challenge.status = 'completed'
                challenge.completed_at = timezone.now()
                
                if challenge.challenger_score > challenge.challenged_score:
                    challenge.winner = challenge.challenger
                elif challenge.challenged_score > challenge.challenger_score:
                    challenge.winner = challenge.challenged
                # else tie
                
                challenge.save()
                
                win_msg_challenger = "You won" if challenge.winner == challenge.challenger else ("You lost" if challenge.winner else "It's a tie")
                win_msg_challenged = "You won" if challenge.winner == challenge.challenged else ("You lost" if challenge.winner else "It's a tie")
                
                Notification.objects.create(
                    user=challenge.challenger,
                    message=f"Challenge completed! {win_msg_challenger} against {challenge.challenged.username} ({challenge.challenger_score} to {challenge.challenged_score})."
                )
                Notification.objects.create(
                    user=challenge.challenged,
                    message=f"Challenge completed! {win_msg_challenged} against {challenge.challenger.username} ({challenge.challenged_score} to {challenge.challenger_score})."
                )
            return JsonResponse({"message": "Score submitted successfully", "status": challenge.status})
        except Challenge.DoesNotExist:
            return JsonResponse({"error": "Challenge not found"}, status=404)
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)

@csrf_exempt
def get_user_challenges(request):
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        if not user_id:
            return JsonResponse({"error": "User ID is required"}, status=400)
        try:
            user = UserCustom.objects.get(id=user_id)
            
            challenges_query = Challenge.objects.filter(models.Q(challenger=user) | models.Q(challenged=user)).order_by("-created_at")
            
            challenges = []
            for c in challenges_query:
                challenges.append({
                    "id": c.id,
                    "challenger": c.challenger.username,
                    "challenged": c.challenged.username,
                    "exercise_type": c.exercise_type,
                    "status": c.status,
                    "challenger_score": c.challenger_score,
                    "challenged_score": c.challenged_score,
                    "winner": c.winner.username if c.winner else None,
                    "created_at": c.created_at
                })
                
            return JsonResponse({"challenges": challenges})
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)

@csrf_exempt
def get_notifications(request):
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        if not user_id:
            return JsonResponse({"error": "User ID is required"}, status=400)
        try:
            user = UserCustom.objects.get(id=user_id)
            notifs = Notification.objects.filter(user=user).order_by("-created_at")
            
            data = []
            for n in notifs:
                data.append({
                    "id": n.id,
                    "message": n.message,
                    "is_read": n.is_read,
                    "created_at": n.created_at
                })
                
            return JsonResponse({"notifications": data})
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)

@csrf_exempt
def mark_notification_read(request):
    if request.method == "POST":
        data = json.loads(request.body) if request.content_type == 'application/json' else request.POST
        notification_id = data.get("notification_id")
        try:
            notif = Notification.objects.get(id=notification_id)
            notif.is_read = True
            notif.save()
            return JsonResponse({"message": "Notification marked as read"})
        except Notification.DoesNotExist:
            return JsonResponse({"error": "Notification not found"}, status=404)
    return JsonResponse({"error": "Invalid request method"}, status=400)
