from django.http import JsonResponse
from django.shortcuts import render
from datetime import date
from .models import DailyActivity
from accounts.models import UserCustom
from django.core.paginator import Paginator, EmptyPage, PageNotAnInteger
# Create your views here.
from django.views.decorators.csrf import csrf_exempt


@csrf_exempt
def log_activity(request):
    if request.method=="POST":
        user_id = request.POST.get("user_id")
        date=request.POST.get("date")
        squats = int(request.POST.get("squats",0))
        pushups = int(request.POST.get("pushups",0))
        steps = int(request.POST.get("steps",0))

        try:
            user = UserCustom.objects.get(id=user_id)
            activity, created = DailyActivity.objects.get_or_create(user=user, date=date)
            activity.squats += squats
            activity.pushups += pushups
            activity.steps += steps
            activity.save()
            return JsonResponse({"message":"Activity logged successfully"})
        
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)
        
@csrf_exempt
def get_activity(request):
    if request.method=="GET":
        user_id = request.GET.get("user_id")
        date=request.GET.get("date")

        try:
            user = UserCustom.objects.get(id=user_id)
            activity = DailyActivity.objects.filter(user=user, date=date).first()
            if activity:
                return JsonResponse({"squats":activity.squats,"pushups":activity.pushups,"steps":activity.steps})
            else:
                return JsonResponse({"error":"No activity found for this date"}, status=404)
        
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)
        
@csrf_exempt
def today(request):
    if request.method=="GET":
        user_id = request.GET.get("user_id")
        try:
            user = UserCustom.objects.get(id=user_id)
            today_activity = DailyActivity.objects.filter(user=user, date=date.today()).first()
            if today_activity:
                return JsonResponse({"squats":today_activity.squats,"pushups":today_activity.pushups,"steps":today_activity.steps})
            else:
                return JsonResponse({"message":"No activity logged for today"})
        
        except UserCustom.DoesNotExist:
            return JsonResponse({"error":"User not found"}, status=404)
        
@csrf_exempt
def activity_history(request, page=1):
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        try:
            user = UserCustom.objects.get(id=user_id)
            activities = DailyActivity.objects.filter(user=user).order_by('-date')
            paginator = Paginator(activities, 10) # Show 10 per page
            
            try:
                activities_page = paginator.page(page)
            except PageNotAnInteger:
                activities_page = paginator.page(1)
            except EmptyPage:
                activities_page = paginator.page(paginator.num_pages)
                
            data = [
                {"date": act.date, "squats": act.squats, "pushups": act.pushups, "steps": act.steps}
                for act in activities_page
            ]
            
            return JsonResponse({
                "activities": data,
                "current_page": activities_page.number,
                "total_pages": paginator.num_pages
            })
            
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)