--分身
Dividebody = InheritsFromBaseAttackSkill();

function Dividebody:Update()
  local me = FightField.fightHum[self.humIdx];

  if self.step == 1 then      
    me.roleController:MagicAttack();
    --風魔法陣
    FightField.attack[self.humIdx]:SetLightWithOrder(1, EEffectLightDis.Ground,10077,50,1,21,21, EEffectLightTracer.StandAni, me.nowPos.x+5, me.nowPos.y,20, true, 90);
    self.interval = 0.8;
    self.step = 2;          
  elseif self.step == 2 then                 
    if self:CheckInterval() then        
      me:SetBeh(EFightBeh.Divide);
      if me.party_Kind == EFightParty.Right then
        me:FDivide(true, me.colm-1, me.row)
      elseif me.party_Kind ==  EFightParty.Left then
        me:FDivide(true, me.colm+1, me.row);
      end
      me:PlayEffect(me.colm, me.row, ERolePose.Prepare);   --隱分身要用的
      self.interval = 1.5;
      self.step = 3;                  
    end
  elseif self.step == 3 then 
    if self:CheckInterval() then        
      self.step = 4;
      self.isLightEnd = true;   --特技施展完畢
    end
  end
end