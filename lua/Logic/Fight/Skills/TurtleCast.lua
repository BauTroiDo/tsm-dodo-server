TurtleCast = InheritsFromBaseAttackSkill();

function TurtleCast:InitExtraParameters()
  self.littleTurtle = {};
  for i = 0, self.emyNum -1 do
    self.littleTurtle[i] = AttackRole.New(ESkill_Kind.TurtleCast, EHuman.Duplicate, 27010);
    self.littleTurtle[i]:SetVisible(ERoleVisible.Hide);
  end
end

function TurtleCast:ExtraClose()
  SetTutrleHandsVisible(ERoleVisible.Visible);
  for k,v in pairs(self.littleTurtle)do
    v:Free();
  end
end

function SetTutrleHandsVisible(status)
  FightField.SetRoleVisibleByID(35033, status);
  FightField.SetRoleVisibleByID(35034, status);
end

function TurtleCast:UpdateTurtle()
  if self.littleTurtle[0].roleController.visible ~= ERoleVisible.Visible then 
    return;
  end

  for k, v in pairs(self.littleTurtle)do
    v:GetPosition();
    v:Update();
  end
end

function TurtleCast:TurtleJump(hum, x, y)
  hum.moveOldTime = CGTimer.time;
  hum.jumpStart = true;
  hum.goal.x = x;
  hum.goal.y = y;  
  hum.totalDist = math.round((hum.goal - hum.nowPos).magnitude);
  if hum.totalDist == 0 then
    hum.jumpStart = false;
  end
end

function TurtleCast:Update()
  local me;
  local newPos;
  local cnt;
  me = FightField.fightHum[self.humIdx];
  self:UpdateTurtle();
  if self.step == 1 then    
    me.roleController:SetAnimationForceId(ERolePose.Magic);    
    me.step = 0;

    SetTutrleHandsVisible(ERoleVisible.Hide);

    self.step = 2;     
  elseif self.step == 2 then 
    if me.roleController.animationFrame == 1 then     
      for i = 0, self.emyNum-1 do        
        self.littleTurtle[i]:SetVisible(ERoleVisible.Visible);
        self.littleTurtle[i]:SetSpeed(0.54);
        newPos = me:GetChipPos();
        self.littleTurtle[i].nowPos.x = newPos.x+112;
        self.littleTurtle[i].nowPos.y = newPos.y+109;              
        newPos.x = FightField.fightHum[self.emyIdxAy[i]].nowPos.x;
        newPos.y = FightField.fightHum[self.emyIdxAy[i]].nowPos.y;          
        self:TurtleJump(self.littleTurtle[i], newPos.x-36, newPos.y-18);
        if me.party_Kind == EFightParty.Right then 
          self.littleTurtle[i].roleController:SetDirection(0);
        else
          self.littleTurtle[i].roleController:SetDirection(4);
        end

        self.littleTurtle[i].roleController:SetInBattle(true);
        self.littleTurtle[i].roleController:OneHandAttack();
        self.littleTurtle[i].roleController:SetAnimationForceFrame(0);        
        self.littleTurtle[i].arrive = false;
      end
      self.step = 3;
    end
  elseif self.step == 3 then 
    cnt = 0;
    for i = 0, self.emyNum-1 do
      if self.littleTurtle[i].arrive then         
        cnt = cnt + 1;
        if self.littleTurtle[i].roleController.animationForceFrame ~= 3 then           
          self.littleTurtle[i].roleController:OneHandAttack();
          self.littleTurtle[i].roleController:SetAnimationForceFrame(1);

          FightField.fightHum[self.emyIdxAy[i]].roleController:Hit();
        end
      end
    end
    if cnt ==(self.emyNum) then       
      self.step = 4;
      self.interval = 0.5;
    end
  elseif self.step == 4 then        
    if self:CheckInterval() then
      self.isLightEnd = true;
      self.step = 5;
    end
  end
end