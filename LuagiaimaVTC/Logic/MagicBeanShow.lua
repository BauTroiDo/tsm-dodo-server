MagicBeanShow = {}
MagicBeanShow.__index = MagicBeanShow;
local this = MagicBeanShow;
  --爬樹相關參數

  this.isClimbTree = false; --爬豆中
  this.beanEffect = nil; --爬豆動畫
  this.magicBeanToEnd = false; --是否達到頂端
  this.magicBeanCircle = false;
  this.roleController = nil;
  this.isMagicBean = false;
  this.magicBeanShowTime = nil;

  this.startPos = Vector2.zero;
  this.endY = 0;

function MagicBeanShow.Start()
  if Role.player.horseNpcId ~= 0 then return end --如果騎馬則不能種魔豆  
  if this.isMagicBean  then return end --如果已經種魔豆則離開

  if this.beanEffect ~= nil then 
    this.beanEffect:Stop();
    this.beanEffect = nil;
  end
  local oriRole = Role.player;                
  this.roleController = RoleController.Copy(oriRole);  
  this.roleController:UpdateViewVisible();

  this.roleController.position = oriRole.position;
  this.roleController.canvas.sortingOrder = 1500  + this.roleController.position.y;
  this.direction =  oriRole.direction;
  this.isClimbTree = false; --爬豆中
  this.beanEffect = nil; --爬豆動畫
  this.magicBeanToEnd = true; --魔豆未到達頂端
  this.magicBeanCircle = false; --魔豆尚未循環
  this.isMagicBean = true;
  this.magicBeanShowTime = CGTimer.time;
  this.startPos = Vector2.New(oriRole.position.x, oriRole.position.y);
  this.climbY = 0;
  this.endY = Scene.cameraPosition.y + Scene.currentSceneHalfHeight;
  
  this.beanEffect = EffectLight.New('MagicBean1',1000,1,4,4, EEffectLightTracer.StandAni, this.startPos.x + 10, this.startPos.y - 20, 255, false, 25, nil, nil, nil, nil, true);
  this.beanEffect:SetSortingOrder(1500);
  Role.player.gameObject:SetActive(false);
  CGTimer.AddListener(this.Update, 0.05, false);
end

function MagicBeanShow.Update()
  if not this.isMagicBean  then  --如果魔豆未種則離開
    return;
  end  
  
  if not this.isClimbTree then
    this.roleController:SetAnimationForceId(ERolePose.Stand);
    this.roleController:SetDirection(ERoleDirection.Up); 

    if CGTimer.time - this.magicBeanShowTime > 5 then  --種魔豆5秒後才開始爬    
      this.isClimbTree = true;  --玩家開始爬樹
      this.magicBeanShowTime = CGTimer.time;
      this.roleController:SetAnimationForceId(ERolePose.Walk);      
    end
  end

  this.ClimbTreeTrace();

  if this.beanEffect.picId == 0 then            --重新載入新圖檔  
    this.beanEffect = EffectLight.New('MagicBean2',1000, 1,10,10, EEffectLightTracer.Stand, this.startPos.x, this.startPos.y, 255, true, 25, nil, nil, nil, nil, true);
    this.beanEffect:SetSortingOrder(1500);
    this.magicBeanToEnd = false;  --開始偵測是否魔豆到達頂端
  end

  if not this.magicBeanToEnd then             
    if this.beanEffect.orderCount == 9 then  --魔豆到達頂端      
      this.magicBeanCircle = true;  --魔豆循環開始
      this.magicBeanToEnd = true;   --停止判斷魔豆是否到達頂端
    end

    if this.magicBeanCircle then    
      this.beanEffect:SetPage(9, 10);
      this.beanEffect.interval = 100;
      this.magicBeanCircle = false; --停止判斷是否循環
    end
  end  
  local screenEdgeY = (Scene.cameraPosition.y - Scene.currentSceneHalfHeight);      
  if (this.roleController.position.y) <= screenEdgeY then     
    Network.Send(23, 45);       --//傳封包給server      
    this.beanEffect:Stop();
    this.beanEffect = nil;      
    this.isClimbTree = false; --以下是將所有相關值歸回初始
    this.isMagicBean = false;
    this.magicBeanToEnd = false;
    this.magicBeanCircle = false;
    this.startPos = Vector2.zero;      
    this.climbY = 0;
    this.roleController:Destroy();      
    this.roleController = nil;
    Role.player.gameObject:SetActive(true);
    CGTimer.RemoveListener(this.Update);
  else
    this.roleController:SetPosition(this.roleController.position);
    this.roleController.canvas.sortingOrder = 1500  + this.roleController.position.y;
    this.roleController:UpdateShow(false, 1);
  end
end

function MagicBeanShow.ClimbTreeTrace()
  if not this.isClimbTree then
    return; 
  end

  local moveValue = 9;  --每次偏移的量
  if CGTimer.time - this.magicBeanShowTime >= 0.2 then  
    this.climbY = this.climbY + moveValue;    
    this.magicBeanShowTime = CGTimer.time;    
    this.roleController.position.y = this.roleController.position.y - moveValue;        
  end 
end