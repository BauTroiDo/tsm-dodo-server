--結界技能&狀態
Boundary = InheritsFromBaseAttackSkill();

function Boundary:Update()
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  local target = FightField.fightHum[self.tarIdx];

  if self.step == 1 then   
    me.roleController:MagicAttack();              
    newPos = target:RCtoSpot(target.colm, target.row);
    if target.party_Kind == EFightParty.Left then           
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky4 ,10007, 30, 1,25,25, EEffectLightTracer.Stand, newPos.x, newPos.y, 40, false, 30);
    elseif target.party_Kind == EFightParty.Right then                     
      FightField.attack[self.humIdx]:SetLight(EEffectLightKind.Sky4 ,10007, 30, 1,25,25, EEffectLightTracer.Stand, newPos.x + 5, newPos.y, 40, false, 30);            
    end
    self.step = 2;
  elseif self.step == 2 then         
    if FightField.attack[self.humIdx].lights[EEffectLightKind.Sky4].orderCount== 24 then      
      self.isLightEnd = true;
      self.step = 3;
    end
  end
end

--專武結界
EW_Boundary = InheritsAttack(Boundary);

function EW_Boundary:InitDrawBlack()
  self.needDrawBlack = true;   
end

BoundaryMe = InheritsFromBaseStatus();

function BoundaryMe:Update()  
  local newPos;
  local me = FightField.fightHum[self.humIdx];
  if self.step == 1 then     
    newPos = me:RCtoSpot(me.colm, me.row);
    local status = me:GetStatus(self.statusKind);
    if me.party_Kind == EFightParty.Left then           
      status:SetLight(EEffectLightKind.Body2,10007, 30, 25, 25, 25, EEffectLightTracer.Stand, newPos.x, newPos.y, 40, false, 30);
    elseif me.party_Kind == EFightParty.Right then           
      status:SetLight(EEffectLightKind.Body2,10007, 30, 25, 25, 25, EEffectLightTracer.Stand, newPos.x + 5, newPos.y, 40, false, 30);
    end            
    self.step = 2;      
  end
end