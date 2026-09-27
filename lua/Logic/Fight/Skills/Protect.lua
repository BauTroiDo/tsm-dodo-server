--護主
Protect = InheritsFromBaseAttackSkill();

function Protect:Update()
  if self.step == 1 then 
    local me  = FightField.fightHum[self.humIdx];
    me:PlayEffect(me.colm, me.row, ERolePose.Hit);
    self.step = 2
  elseif self.step == 2 then   
    self.isLightEnd = true; --特技施展完畢
  end
end

--狀態顯示
ProtectMe = InheritsFromBaseStatus();

function ProtectMe:ExtraClose()  
  FightField.RestoreFightInfo(self.humIdx, 0);  
end

function ProtectMe:Update()
  local me = FightField.fightHum[self.humIdx];

  if self.step == 1 then     
    local target;
    local blnFind = false;

    for k,v in pairs(FightField.fightHum)do 
      if v.id == me.masterID then    
        target = v;
        blnFind = true;
        break;
      end
    end
  

    local newPos;
    if blnFind then 
      newPos = target:GetAttackPos(EFightSpot.Front, 35);
      me:SetMoment(newPos.x, newPos.y, false);
      me.roleController:Block();
      me.roleController:SetAnimationForceId(me.roleController:GetAnimationId(ERolePose.Defend))
      --狀態備份
      FightField.StatusBkUPFightInfo(self.humIdx);      
    end
    self.step = 2;
  end
end


--解除護主
DisProtect = {};
DisProtect.__index = DisProtect;

function DisProtect.New(humIdx)
  local self = {};
  setmetatable(self, DisProtect);
  self.humIdx = humIdx;
  self.isLightEnd = true;

  FightField.fightHum[self.humIdx]:SetStatus(EFightStatusKind.Spec, ESkill_Kind.Dis)    
  return self;
end

function DisProtect:Free()
  FightField.RestoreFightInfo(self.humIdx) 
end

function DisProtect:Update()

end
