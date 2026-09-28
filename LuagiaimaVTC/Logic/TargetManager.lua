TargetManager = {};
local this = TargetManager;

local targets = {};

local function GetNextOneTarget()
  local currentKey  = -1;
  local foundTarget = nil;
  local firstTarget = nil;

  for k, v in pairs(targets) do
    if firstTarget == nil then
      firstTarget = v;
    end  

    -- 多個Target
    if Role.target ~= nil and table.maxn(targets) > 1 then 
      -- 找到當前目標後,下個迴圈設定目標
      if currentKey ~= -1 and foundTarget == nil then
        foundTarget = v;
      end
      
      if Role.target == v then 
        currentKey = k; 
      end
    end 
  end

  if foundTarget == nil then
    --所選是最後一個 or 清單中只有一個目標 or 未選任何目標時
    return firstTarget;
  else 
    return foundTarget;
  end
end

function TargetManager.ExitScene()
  this.CancelTarget();
end

function TargetManager.Add(role)
  if table.Contains(targets, role) then return end
  if role.kind == EHuman.MapNpc and npcDatas[role.npcId] ~= nil and Contains(npcDatas[role.npcId].kind, ENpc.Mine) then return end
  
  table.insert(targets, role);

  --NPC優先
  table.sort(targets, function(a, b) return a.kind > b.kind end);

  --目前有目標且非移動中不自動選取
  if Role.target ~= nil and not Role.player.isMoving then return end

  --任務導航中不自動選取
  if MarkManager.navigationMissionId ~= 0 and not EventManager.isChangingScene then
    MarkManager.CheckNavigationNpc(role);
    return;
  end

  --只有目標人物要自動選取
  if role ~= Role.moveTarget then return end
  
  Role.SetTarget(role);
end

function TargetManager.Remove(role)
  if role == nil then return end
  if not table.RemoveByValue(targets, role) then return end
  
  --非當前目標不取消
  if role ~= Role.target then return end
  
  this.ChangeTarget();
end

function TargetManager.CancelTarget()
  for k, v in pairs(targets) do
    if v ~= nil then
      v.isTrigged = false;
    end
  end
  
  table.Clear(targets);

  Role.SetTarget(nil);
end

function TargetManager.ChangeTarget(role, notInteract)
  if role ~= nil then
    if not this.Contains(role) then return end
  else
    role = GetNextOneTarget();
  end
  if notInteract then
    if Role.target ~= nil then
      Role.target:SetSelected(false);
    end
    Role.target = role;
    if Role.target ~= nil then
      Role.target:SetSelected(true);
    end
  else
    Role.SetTarget(role);
  end
end

function TargetManager.Contains(role)
  for k, v in pairs(targets) do
    if v == role then
      return true;
    end
  end

  return false;
end