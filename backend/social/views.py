from django.shortcuts import render
from django.http import JsonResponse
from django.views.decorators.csrf import csrf_exempt
from accounts.models import UserCustom
from .models import Post, Like

@csrf_exempt
def get_feed(request, page):
    if request.method == "GET":
        user_id = request.GET.get("user_id")
        posts = Post.objects.all().select_related("user").order_by("-created_at")
        start = (page - 1) * 10
        end = start + 10
        
        posts_page = posts[start:end]
        
        feed_data = []
        for post in posts_page:
            likes_count = Like.objects.filter(post=post).count()
            is_liked_by_me = False
            if user_id:
                try:
                    is_liked_by_me = Like.objects.filter(post=post, user_id=user_id).exists()
                except Exception:
                    pass

            feed_data.append({
                "id": post.id,
                "author_username": post.user.username,
                "content": post.content,
                "created_at": post.created_at.isoformat(),
                "likes_count": likes_count,
                "is_liked": is_liked_by_me
            })
            
        return JsonResponse({"posts": feed_data})

@csrf_exempt
def create_post(request):
    if request.method == "POST":
        user_id = request.POST.get("user_id")
        content = request.POST.get("content")
        
        if not user_id or not content:
            return JsonResponse({"error": "Missing fields"}, status=400)
            
        try:
            user = UserCustom.objects.get(id=user_id)
            post = Post.objects.create(user=user, content=content)
            return JsonResponse({
                "message": "Post created", 
                "post": {
                    "id": post.id,
                    "author_username": user.username,
                    "content": post.content
                }
            })
        except UserCustom.DoesNotExist:
            return JsonResponse({"error": "User not found"}, status=404)
        except Exception as e:
            return JsonResponse({"error": str(e)}, status=500)
    return JsonResponse({"error": "Invalid method"}, status=405)

@csrf_exempt
def like_post(request):
    if request.method == "POST":
        user_id = request.POST.get("user_id")
        post_id = request.POST.get("post_id")
        
        try:
            user = UserCustom.objects.get(id=user_id)
            post = Post.objects.get(id=post_id)
            
            like, created = Like.objects.get_or_create(user=user, post=post)
            if not created:
                like.delete()
                liked = False
            else:
                liked = True
                
            likes_count = Like.objects.filter(post=post).count()
            return JsonResponse({"message": "Success", "liked": liked, "likes_count": likes_count})
        except Exception as e:
            return JsonResponse({"error": str(e)}, status=400)
    return JsonResponse({"error": "Invalid method"}, status=405)
