import json
from django.http import JsonResponse
from accounts.models import UserCustom
from gamification.models import UserStats, XPLog
from .models import RewardItem, Redemption

# Level ranking to determine if user can unlock a reward
LEVEL_ORDER = {
    "Tortoise": 1,
    "Wolf": 2,
    "Eagle": 3,
    "Leopard": 4,
    "Lion": 5
}

def get_rewards(request):
    """
    GET /api/reward/list/?user_id=<uuid>
    Returns all available rewards in the store, 
    and annotates them with whether the user has enough coins and the required Beast Level.
    """
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        
        user_level = "Tortoise"
        user_coins = 0
        
        if user_id:
            try:
                user = UserCustom.objects.get(id=user_id)
                stats, _ = UserStats.objects.get_or_create(user=user)
                user_level = stats.level
                user_coins = stats.fitcoins
            except UserCustom.DoesNotExist:
                return JsonResponse({"error": "User not found"}, status=404)
        else:
            return JsonResponse({"error": "user_id is required"}, status=400)
            
        rewards = RewardItem.objects.all()
        data = []
        
        user_level_rank = LEVEL_ORDER.get(user_level, 1)
        
        for item in rewards:
            item_level_rank = LEVEL_ORDER.get(item.required_level, 1)
            
            # Check conditions
            level_unlocked = user_level_rank >= item_level_rank
            affordable = user_coins >= item.coin_cost
            in_stock = item.stock > 0
            
            data.append({
                "id": item.id,
                "name": item.name,
                "description": item.description,
                "coin_cost": item.coin_cost,
                "required_level": item.required_level,
                "stock": item.stock,
                "level_unlocked": level_unlocked,
                "affordable": affordable,
                "in_stock": in_stock,
                "can_redeem": level_unlocked and affordable and in_stock
            })
            
        return JsonResponse({
            "user_fitcoins": user_coins,
            "user_level": user_level,
            "rewards": data
        })

def redeem_reward(request):
    """
    POST /api/reward/redeem/
    Expects user_id and item_id. Performs redemption if user is eligible.
    """
    if request.method == "POST":
        user_id = request.POST.get("user_id")
        item_id = request.POST.get("item_id")
        
        if not user_id or not item_id:
            return JsonResponse({"error": "Missing user_id or item_id"}, status=400)
            
        try:
            user = UserCustom.objects.get(id=user_id)
            stats = UserStats.objects.get(user=user)
            item = RewardItem.objects.get(id=item_id)
            
            user_level_rank = LEVEL_ORDER.get(stats.level, 1)
            item_level_rank = LEVEL_ORDER.get(item.required_level, 1)
            
            if user_level_rank < item_level_rank:
                return JsonResponse({"error": f"Level too low. Requires {item.required_level} level."}, status=403)
                
            if stats.fitcoins < item.coin_cost:
                return JsonResponse({"error": "Not enough FitCoins."}, status=403)
                
            if item.stock <= 0:
                return JsonResponse({"error": "Item out of stock."}, status=400)
                
            # Perform transaction
            stats.fitcoins -= item.coin_cost
            stats.save()
            
            item.stock -= 1
            item.save()
            
            Redemption.objects.create(
                user=user,
                item=item,
                status="Pending Delivery/Claim"  # Mock status
            )
            
            # Log the spend optionally in XPLog or another transaction log
            XPLog.objects.create(user=user, source=f"Redeemed: {item.name}", xp_amount=0) # reusing XPLog just as an activity trace or create a new CoinTransaction model later
            
            return JsonResponse({
                "message": f"Successfully redeemed {item.name}!",
                "remaining_fitcoins": stats.fitcoins
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
        except UserStats.DoesNotExist:
            return JsonResponse({"error": "User stats not found (No FitCoins)."}, status=404)
        except RewardItem.DoesNotExist:
            return JsonResponse({"error": "Reward item not found."}, status=404)
            
    return JsonResponse({"error": "Invalid method. Expected POST."}, status=405)

def user_redemptions(request):
    """
    GET /api/reward/history/?user_id=<uuid>
    Returns user's past redemptions.
    """
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        if not user_id:
            return JsonResponse({"error": "user_id is required"}, status=400)
            
        try:
            user = UserCustom.objects.get(id=user_id)
            redemptions = Redemption.objects.filter(user=user).order_by('-redeemed_at')
            
            data = []
            for r in redemptions:
                data.append({
                    "id": r.id,
                    "item_name": r.item.name,
                    "coin_cost": r.item.coin_cost,
                    "status": r.status,
                    "redeemed_at": r.redeemed_at.strftime("%Y-%m-%d %H:%M:%S")
                })
                
            return JsonResponse({"history": data})
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
