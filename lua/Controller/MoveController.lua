
MoveController = {}
MoveController.__index = MoveController;

local BeePath = {
  {-100, -100},
  {-100, 100},
  {100, 100},
  {100, -100},
  {100, -100},
  {100, 100},
  {-100, 100},
  {-100, -100}
};

MoveController.PalyerMoveTime = 0.4;

function MoveController.New(roleController)
  local self = {};
  setmetatable(self, MoveController);

  self.roleController = roleController;

  self.targetPosition = Vector2.zero;

  self.movePath = {};

  self.isMoveDefaultPath = false;
  self.isMoveRandomPath = false;
  self.pathMedIndex = 0;
  self.pathMed = {};
  self.pathCount = 1;

  self.callback = nil;  

  --bee
  self.beeIndex = 0;
  self.beeTargets = {};

  return self;
end

function MoveController:MoveToLocalTarget(targetPosition, callback, isMoveStraight, isMoveCurrentView, isMoveLine, isBeeMove)
  self.callback = callback;
  self.movePath = FindWay.Start(self.roleController.position, targetPosition, isMoveStraight, isMoveCurrentView, isMoveLine, self.roleController);

  local target = self:GetCurTarget();

  --小屋裡，玩家卡住傳到離目標方向最近的位置
  if HouseManager.IsInHouse() and self.roleController.kind == EHuman.Player and not FurnitureManager.GetPlayerIsInteracting() then
    if target == nil or target == self.roleController.position then
      local startBlockX, startBlockY = MapManager.GetBlock(self.roleController.position);

      if MapManager.IsObstacle(startBlockX, startBlockY) then
        local tempX = targetPosition.x - MapManager.centerLeft;
        local tempY = targetPosition.y - MapManager.centerTop;

        if tempX < 0 then tempX = 0 end
        if tempY < 0 then tempY = 0 end

        local targetBlockX = math.ceil(tempX * MapManager.BLOCK_CONVERT);
        local targetBlockY = math.ceil(tempY * MapManager.BLOCK_CONVERT);

        targetBlockX, targetBlockY = MapManager.GetStartEmptyBlockWithDirect(startBlockX, startBlockY, targetBlockX, targetBlockY);
        if not MapManager.IsObstacle(targetBlockX, targetBlockY) then
          local newPosition = Vector2.New(MapManager.GetPosition(Vector2.New(targetBlockX, targetBlockY)));
          UIJoystick.StopClickEffect();
          HouseManager.TeleportPlayer(self.roleController, newPosition);
        end
      end
    end
  end

  if target == nil then
    self:StopMove(false);
    return;
  end
  
  if self.roleController.kind == EHuman.Player and MachineBox.client.general[EMachineBoxSwitch.SampleMode] and Role.player.war ~= EWar.None then return end
  
  if self.roleController.kind == EHuman.Player then
    MachineBox.autoMoving = isBeeMove == true;
    UIJoystick.SetClickEffect(self.movePath[#self.movePath]);
  end

  self:StartMove(target);
end

function MoveController:StartMove(target)
  --小屋裡，武將卡住傳到最近的地方
  if target == self.roleController.position and HouseManager.IsInHouse() and self.roleController.kind == EHuman.HouseWarrior then
    local targetBlockX, targetBlockY = MapManager.GetStartEmptyBlock(MapManager.GetBlock(self.roleController.position));

    if not MapManager.IsObstacle(targetBlockX, targetBlockY) then
      local newPosition = Vector2.New(MapManager.GetPosition(Vector2.New(targetBlockX, targetBlockY)));
      self.roleController:Teleport(newPosition);
      self:StartRandomPath();
      return;
    end
  end

  if target == self.roleController.position then
    self:StopMove(true);
    return;
  end

  self.targetPosition = target;

  self.roleController:FaceTo(self.targetPosition);
  self.roleController:SetIsMoving(true);

  if self.roleController.kind == EHuman.Player then
    if not CGTimer.ContainsListener(MoveController.SendRolePosition) then
      CGTimer.AddListener(MoveController.SendRolePosition, MoveController.PalyerMoveTime, true);
    end
  end
end

function MoveController:StopMove(moveDone)
  self.targetPosition.x = 0;
  self.targetPosition.y = 0;
  self.roleController:SetIsMoving(false);

  if self.callback ~= nil then
    moveDone = moveDone or false;
    
    local callback = self.callback;
    
    self.callback = nil;
    
    callback(self.roleController, moveDone);
  end
end

function MoveController:StartDefaultPath()
  self.isMoveDefaultPath = true;

  local target = self:GetCurTarget();
  if target == nil then
    return;
  end

  self:StartMove(target);
end

function MoveController:StopDefaultPath()
  self.isMoveDefaultPath = false;
  self:StopMove()
end

function MoveController:StartRandomPath()
  self.isMoveRandomPath = true;

  local target = self:GetCurTarget();

  if target == nil then return end

  self:StartMove(target);
end

function MoveController:StopRandomPath()
  self.isMoveRandomPath = false;
  self:StopMove()
end

function MoveController:Update()
  if self.roleController.kind == EHuman.Player and FightField.isInBattle then return end --戰鬥中，不移動

  if self.targetPosition.x == 0 and self.targetPosition.y == 0 then
    if self.roleController.kind == EHuman.Player then
      self:AutoMove();
    end

    return;
  end

  self.moveDist = CGTimer.deltaTime * self.roleController.speed;
  
  local moveDirection = self.targetPosition - self.roleController.position;
  
  if moveDirection.sqrMagnitude <= self.moveDist * self.moveDist then
    --到終點
    self.roleController:SetPosition(self.targetPosition);
    
    self:RemoveCurTarget();
    local target = self:GetCurTarget();
    if target == nil then
      self:StopMove(true);
    else
      self:StartMove(target);
    end
  else
    --還沒到終點
    self.roleController:SetPosition(self.roleController.position + moveDirection.normalized * self.moveDist);
  end
end

function MoveController:GetCurTarget()
  if self.isMoveDefaultPath then
    local result;
    if self.pathMedIndex <= 0 then
      self.pathMedIndex = 1;
      self.pathCount = 1;
      self.pathMedIndex = self.pathMedIndex + self.pathCount;
    elseif self.pathMedIndex > table.Count(self.pathMed) then
      self.pathMedIndex = table.Count(self.pathMed);
      self.pathCount = -1;
      self.pathMedIndex = self.pathMedIndex + self.pathCount;
    end

    result = Vector2.New(self.pathMed[self.pathMedIndex].x, self.pathMed[self.pathMedIndex].y);
    self.pathMedIndex = self.pathMedIndex + self.pathCount;

    return result;
  elseif self.isMoveRandomPath then
    local result = self.GetRandomNextPosition(self.roleController.position, self.roleController.direction);
    return result;
  else
    if table.Count(self.movePath) <= 0 then
      return nil;
    end

    return Vector2.New(MapManager.GetPosition(self.movePath[1]));
  end
end

function MoveController:RemoveCurTarget()
  table.remove(self.movePath, 1);
end

function MoveController.SendRolePosition()
  if FightField.isInBattle or not Role.CanControl() then return end --戰鬥中或不可控制，不移動

  if Role.player.moveController.targetPosition.x == 0 and Role.player.moveController.targetPosition.y == 0 then --沒目標，結束移動
    MoveController.SendMove(Role.player.position.x, Role.player.position.y);
    CGTimer.RemoveListener(MoveController.SendRolePosition);
  else
    if UIJoystick.IsDrag() then
      MoveController.SendMove(Role.player.position.x, Role.player.position.y);
    else
      local pos = MoveController.GetNextPosition();
      MoveController.SendMove(pos.x, pos.y);
    end
  end
end

function MoveController.GetNextPosition()
  local pos;
  local moveDist = Role.player.speed;--MoveController.PalyerMoveTime * Role.player.speed;
  local nowPostion = Vector2.New( math.ceil(Role.player.position.x),  math.ceil(Role.player.position.y) );
  local moveDirection = Role.player.moveController.targetPosition - nowPostion;
  
  if moveDirection.sqrMagnitude <= moveDist * moveDist then
    local target =  Role.player.moveController:GetCurTarget();
    if target == nil then
      pos = Role.player.moveController.targetPosition;
    else
      pos = target;
    end
  else
    pos = nowPostion + moveDirection.normalized * moveDist;
  end

  return pos;
end

function MoveController.GetRandomNextPosition(current_Position, faceTo)
  local result = current_Position;
  local direction = math.random(0, 63);
  local blockX, blockY = MapManager.GetBlock(current_Position);
  local targetBlockX, targetBlockY;

  if direction >= 8 then
    direction = faceTo;
  end

  for i = 0, 7 do
    local newDirection = direction + i;
    newDirection = newDirection % 8;
    targetBlockX, targetBlockY = MoveController.GetNewBlockByDirection(blockX, blockY, newDirection);
    if not MapManager.IsObstacle(targetBlockX, targetBlockY) then
      result = Vector2.New(MapManager.GetPosition(Vector2.New(targetBlockX, targetBlockY)));
      return result;
    end
  end
  result = current_Position;

  return result;
end

function MoveController.GetNewBlockByDirection(blockX, blockY, direction)
  if direction == EFaceDirection.Up then
    blockY = blockY - 1;
  elseif direction == EFaceDirection.LeftUp then
    blockX = blockX - 1;
    blockY = blockY - 1;
  elseif direction == EFaceDirection.Left then
    blockX = blockX - 1;
  elseif direction == EFaceDirection.LeftDown then
    blockX = blockX - 1;
    blockY = blockY + 1;
  elseif direction == EFaceDirection.Down then
    blockY = blockY + 1;
  elseif direction == EFaceDirection.RightDown then
    blockX = blockX + 1;
    blockY = blockY + 1;
  elseif direction == EFaceDirection.Right then
    blockX = blockX + 1;
  elseif direction == EFaceDirection.RightUp then
    blockX = blockX + 1;
    blockY = blockY - 1;
  end
  
  return blockX, blockY;
end

function MoveController.SendMove(x, y)
  --C:006-001 <玩家移動> +方向(1) +座標X(2) +座標Y(2)
  sendBuffer:Clear()
  sendBuffer:WriteByte(Role.player.direction);
  sendBuffer:WriteUInt16(x);
  sendBuffer:WriteUInt16(y);
  Network.Send(6, 1, sendBuffer);
end

--bee
local nextMoveTime = 0;
function MoveController:AutoMove()
  if CGTimer.time < nextMoveTime then return end

  if not MachineBox.autoMove then return end
  if UIJoystick.IsDrag() then return end

  local mode = MachineBox.GetMoveMode();

  local traceMoveTarget = false;
  local tracePatrolNpc = true;
  local traceNpcId = 0;
  local traceCallback = nil;

  if Define.SuperAI then
    if SceneManager.dungeonId ~= 0 then
      mode = EAutoMoveMode.Chase;

      --traceMoveTarget = false;
      tracePatrolNpc = false;

      if UI.IsVisible(UICheck) then
        UICheck.OnClick_Next();
        return;
      end

      if UI.IsVisible(UIResult) then
        UIResult.OnClickfun1();
        MachineBox.SetAutoMove(false);
        return;
      end

      local traceMissionId = 0;
      for k, v in pairs(MarkManager.showMissions) do
        if markDatas[v] ~= nil and Contains(markDatas[v].kind, 4, 5) then
          traceMissionId = v;
          break;
        end
      end

      if Contains(traceMissionId, 12331, 12347, 12351, 12355) then
        --找寶箱
        traceNpcId = 20001;
      elseif traceMissionId == 12359 then
        --找巴豆妖1
        traceNpcId = 22087;
      elseif traceMissionId == 12363 then
        --找巴豆妖2
        traceNpcId = 22088;
      elseif traceMissionId == 12367 then
        --找巴豆妖3
        traceNpcId = 22089;
      elseif traceMissionId == 12371 then
        --找巴豆妖4
        traceNpcId = 22090;
      elseif traceMissionId ~= 0 then
        --多人副本
        MarkManager.Navigation(traceMissionId);
      end

      if traceNpcId == 0 then return end

      if self.targetPosition.x ~= 0 or self.targetPosition.y ~= 0 then return end

      traceCallback =
        function(roleController, moveDone)
          if not moveDone then return end

          TargetManager.ChangeTarget();
          if Role.target ~= nil and Role.target.npcId == traceNpcId then
            Role.target:OnInteractive(EInteractive.Trig);
          end
        end
      
    else
      --traceMoveTarget = true;
      tracePatrolNpc = true;
    end
  end

  if EventManager.inBattle then return end
  if EventManager.IsRunning() then return end
  
  if mode == EAutoMoveMode.Chase then
    local chaseNpc = nil;
    local chaseDistance = nil;
    local distance = 0;
    local ignore = false;
    
    for k, v in pairs(Role.mapNpcs) do
      ignore = false;

      --檢查是否可視
      if not ignore and (v.visible == ERoleVisible.Hide or v.visible == ERoleVisible.TimeHide) then
        ignore = true;
      end

      --檢查是否鎖定目標
      if not ignore and Define.SuperAI then
        if traceNpcId ~= 0 then
          if v.npcId ~= traceNpcId then
            ignore = true;
          end
        elseif MachineBox.client.mobs[EMachineBoxRole.Player] ~= 10 then
          if k ~= MachineBox.client.mobs[EMachineBoxRole.Player] then
            ignore = true;
          end
        end
      end
      
      --檢查是否追擊 NPC
      if not ignore and tracePatrolNpc and v.data.traceRadius <= 0 then
        ignore = true;
      end

      --檢查是否追擊屬性
      if not ignore and npcDatas[v.npcId] ~= nil and not MachineBox.CheckChaseElement(npcDatas[v.npcId].element) then
        ignore = true;
      end
      
      --[[ 因應SERVER撞怪邏輯改變，不再需要做此檢查
      --檢查是否戰鬥中
      if not ignore then
        for kk, vv in pairs(Role.players) do
          if vv.index ~= Role.playerId and vv.war ~= EWar.None then
            if (v.position - vv.position).sqrMagnitude < 100 then
              ignore = true;
              break;
            end
          end
        end
      end
      --]]

      if not ignore then
        if traceMoveTarget and v.moveController ~= nil and v.moveController.targetPosition.x ~= 0 and v.moveController.targetPosition.y ~= 0 then
          distance = (Role.player.position - v.moveController.targetPosition).sqrMagnitude;
        else
          distance = (Role.player.position - v.position).sqrMagnitude;
        end

        if chaseDistance == nil or chaseDistance > distance then
          chaseNpc = v;
          chaseDistance = distance;
        end
      end
    end

    if chaseNpc ~= nil then
      if traceMoveTarget and chaseNpc.moveController ~= nil and chaseNpc.moveController.targetPosition.x ~= 0 and chaseNpc.moveController.targetPosition.y ~= 0 then
        self:MoveToLocalTarget(chaseNpc.moveController.targetPosition, traceCallback);
      else
        self:MoveToLocalTarget(chaseNpc.position, traceCallback);
      end

      if self.targetPosition ~= Vector2.zero then
        CGTimer.DoFunctionDelay(1, function() self:AutoMove(); end);
      else
        nextMoveTime = CGTimer.time + 1;
      end

      return;
    end
  end

  if not MachineBox.autoMoving then
    local target = Vector2.zero;
    
    target.x = self.roleController.position.x;
    target.y = self.roleController.position.y;

    for k, v in pairs(BeePath) do
      target.x = target.x + BeePath[k][1];
      target.y = target.y + BeePath[k][2];

      if self.beeTargets[k] == nil then
        self.beeTargets[k] = Vector2.zero;
      end

      self.beeTargets[k].x = target.x;
      self.beeTargets[k].y = target.y;
    end

    self.beeIndex = 1;
  else
    self.beeIndex = self.beeIndex + 1;
  end
  
  if table.maxn(self.beeTargets) == 0 then return end
  
  if self.beeIndex > table.maxn(self.beeTargets) then
    self.beeIndex = 1;
  end
  
  self:MoveToLocalTarget(self.beeTargets[self.beeIndex], nil, false, true, false, true);
end
