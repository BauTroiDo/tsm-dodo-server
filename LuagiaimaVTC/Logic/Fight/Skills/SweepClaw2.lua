SweepClaw2 = InheritsFromBaseAttackSkill();

function SweepClaw2:InitExtraParameters()
  self.fire = false;                  --是否打出火光
  self.waterLight = {};
end

 function SweepClaw2:ExtraClose()
  ClearLightTable(self.waterLight);  
end

function SweepClaw2:Update()
  local target;
  local total;
  local newPos;
  local me = FightField.fightHum[self.humIdx];

  if self.step == 1 then     
    me.roleController:SetIsDead(true);     
    me.roleController:SetAnimationForceFrame(0);
 
    self.interval = 0.3;
    self.step = 2;      
  elseif self.step == 2 then       
    if self:CheckInterval() then        
      me.roleController:SetAnimationForceFrame(3);
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        me:PlaySound(target.colm, target.row);
        me:PlayEffect(target.colm, target.row, ERolePose.Hit, 4);

        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機
        if not target:IsInSealStatus() then     
          target:SetWalk(target.nowPos.x, target.nowPos.y-118, false);
        end

        self.waterLight[i] = EffectLight.New(10046, 80, 1, 13, 13, EEffectLightTracer.StandAni, target.nowPos.x, target.nowPos.y, 30);
        self.waterLight[i]:SetTimeScale(FightField.timeScale);   
        self.waterLight[i]:SetSortingOrder(FightField.FIGHT_BODYLIGHT_ORDER);
      end
      self.step = 3;        
    end
  elseif self.step == 3 then       
    total = 0;
    for i=0, self.emyNum-1 do        
      target = FightField.fightHum[self.emyIdxAy[i]];
      if target.arrive then
        total = total + 1;
      end
    end

    if total == self.emyNum then        
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetSpeed(0.12);

        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機            
        if not target:IsInSealStatus() then    
          target:SetJump(target.nowPos.x+20, target.nowPos.y);
        end

        me:PlayEffect(target.colm, target.row, ERolePose.Hit,4);
      end
      self.step = 4;        
    end
  elseif self.step == 4 then       
    total = 0;
    for i=0, self.emyNum-1 do        
      target = FightField.fightHum[self.emyIdxAy[i]];
      if target.arrive then
        total = total + 1;
      end
    end

    if total == self.emyNum then        
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetSpeed(0.12);

        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機            
        if not target:IsInSealStatus() then     
          target:SetJump(target.nowPos.x-20, target.nowPos.y);
        end

        me:PlayEffect(target.colm, target.row, ERolePose.Hit, 4);
      end
      self.step = 5;        
    end
  elseif self.step == 5 then 
    total = 0;
    for i=0, self.emyNum-1 do        
      target = FightField.fightHum[self.emyIdxAy[i]];
      if target.arrive then
        total = total + 1;
      end
    end

    if total == self.emyNum then        
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetSpeed(0.12);

        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機 
        if not target:IsInSealStatus() then      
          target:SetJump(target.nowPos.x-20, target.nowPos.y);
        end

        me:PlayEffect(target.colm, target.row, ERolePose.Hit, 4);
      end
      self.step = 6;        
    end
  elseif self.step == 6 then 
    total = 0;
    for i=0, self.emyNum-1 do        
      target = FightField.fightHum[self.emyIdxAy[i]];
      if target.arrive then
        total = total + 1;
      end
    end

    if total == self.emyNum then        
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetSpeed(0.12);

        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機 
        if not target:IsInSealStatus() then      
          target:SetJump(target.nowPos.x+20, target.nowPos.y);
        end

        me:PlayEffect(target.colm, target.row, ERolePose.Hit, 4);
      end
      self.step = 7;        
    end
  elseif self.step == 7 then           
    total = 0;
    for i=0, self.emyNum-1 do        
      target = FightField.fightHum[self.emyIdxAy[i]];
      if target.arrive then
        total = total + 1;
      end
    end

    if total == self.emyNum then        
      for i=0, self.emyNum-1 do          
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetBeh(EFightBeh.Quake);   --設定行為
            
        --030613J固態化不做跳移的動作,旋風status時取得方向不對=>會當機 
        if not target:IsInSealStatus() then
          target:SetWalk(target.nowPos.x, target.nowPos.y);
        end            
        target:SetSpeed(0.72);        
      end
      self.step = 8;        
    end      
  elseif self.step == 8 then 
    if self.waterLight[0] == nil or self.waterLight[0].picId ==0 then      
      for i=0, self.emyNum-1 do        
        target = FightField.fightHum[self.emyIdxAy[i]];
        target:SetBeh(EFightBeh.None);

        newPos = target:GetChipPos();
        target:SetMoment(newPos.x, newPos.y, false);
        --target.isShade=True;
      end

      self.step = 9;
      self.isLightEnd = true;
    end    
  end
end