--region TweenPack.lua
TweenPacker = {};
local this = TweenPacker;

function TweenPacker.GetEmptyTween(emptyValue, duration)    
  return TweenPack.GetEmptyTween(emptyValue, duration);
end

function TweenPacker.GetEmptySequence()
  return TweenPack.GetEmptySequence()
end

function TweenPacker.AppendCallback(sequence, func, duration)
  sequence:AppendCallback(func);
  if duration > 0 then
    TweenPacker.CountingTime(sequence, duration, ETweenAddKind.Append);
  end
end

function TweenPacker.PrependCallback(sequence, func, duration)  
  if duration > 0 then
    TweenPacker.CountingTime(sequence, duration, ETweenAddKind.Prepend);
  end
  sequence:PrependCallback(func);    
end   

function TweenPacker.CountingTime(sequence, duration, addKind)  
  TweenPack.DoCountingTime(sequence, duration, 1, addKind);
end
--region TweenAction
function TweenPacker.DoLocalMove(sequence, trans, pos_v3, duration, addKind)
  TweenPack.DoLocalMove(sequence, trans, pos_v3, duration, addKind);
end

function TweenPacker.DoMove(sequence, trans, pos_v3, duration, addKind)
  TweenPack.DoMove(sequence, rectTrans, pos_v3, duration, addKind);
end

function TweenPacker.DoRotate(sequence, trans, eulerAngle_v3, duration, addKind)
  TweenPack.DoRotate(sequence, trans, eulerAngle_v3, duration, addKind);
end

function TweenPacker.DoShake(sequence, trans, duration, strength_v3, vibrato, randomness, addKind)
  TweenPacker.DoShake(sequence, trans, duration, strength_v3, vibrato, randomness, addKind);
end

function TweenPacker.DoLookAt(sequence, trans, pos_v3, duration, addKind)
  TweenPacker.DoLookAt(sequence, trans, pos_v3, duration, addKind);
end
--endregion

--region TweenCustomAction
function TweenPacker.Talk(sequence, callBack, content, roleController, duration, addKind)    
  if addKind == ETweenAddKind.Prepend then                  
    this.PrependCallback(sequence, function() ShowTalkMessage(callBack, content, roleController) end, duration);       
  else
    this.AppendCallback(sequence, function() logError(string.Concat("talk duration: ", duration)); ShowTalkMessage(callBack, content, roleController) end, duration); 
  end         
end

function TweenPacker.Visible(sequence, roleController, visible, duration, addKind)
  if addKind == ETweenAddKind.Prepend then
    this.PrependCallback(sequence, function() roleController:SetVisible(visible, 0); end, duration)
  else
    this.AppendCallback(sequence, function() roleController:SetVisible(visible, 0); end, duration)
  end
end

function TweenPacker.Emoji(sequence, roleController, emojiID, duration, addKind)
  if addKind == ETweenAddKind.Prepend then
    this.PrependCallback(sequence, function() roleController:PlayEmoji(emojiID); end, duration)
  else
    this.AppendCallback(sequence, function() roleController:PlayEmoji(emojiID); end, duration)
  end
end

function TweenPacker.PathMove(sequence, roleController, pos_v2, duration, addKind, callback)
  if addKind == ETweenAddKind.Prepend then
    this.PrependCallback(sequence, function() roleController:Move(pos_v2, callback); end, duration);
  else
    this.AppendCallback(sequence, function() roleController:Move(pos_v2, callback); end, duration);
  end
end

function TweenPacker.Position(sequence, roleController, pos_v2, duration, addKind)
  if addKind == ETweenAddKind.Prepend then
    this.PrependCallback(sequence, function() roleController:SetPosition(pos_v2); end, duration);
  else
    this.AppendCallback(sequence, function() roleController:SetPosition(pos_v2); end, duration);
  end
end
--endregion

--endregion
