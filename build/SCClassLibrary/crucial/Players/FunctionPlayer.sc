
FunctionPlayer : AbstractPlayer {

	var <function,synthDef;
	var numChannels,rate; // override methods
	
	*new { arg function;
		^super.new.init(function)
	}
	init { arg f;
		function = f;
		synthDef = this.asSynthDef;
	}
	ar {	// can still be used as a building block
		^function.value
	}
	// for now, can't correctly determine due to SynthDef
	// limitations
	rate { ^'audio' }
	numChannels { ^2 }
	
	defName {
		^(this.class.name.asString ++ function.identityHash.asString)
	}
	asSynthDef { 
		^SynthDef(this.defName,{ arg outIndex = 0;
			var result;
			result = this.ar;
			if(result.isArray,{
				rate = result.first.rate;
				numChannels = result.size;
			},{
				rate = result.rate;
				numChannels = 1;
			});
			if(rate == \audio,{
				Out.ar(outIndex,result)
			},{
				Out.kr(outIndex,result)
			});
		})
	}
	//*initClass {  kill files }

}



PlayerEfxFunc : FunctionPlayer {

	var <>efxFunction;
	
	*new { arg function, efxFunction;
		^super.new( function ).efxFunction_(loadDocument( efxFunction ))
	}
	
	ar {
		^efxFunction.value(function.value)
	}
	
	storeParamsOn { arg stream; 
		// although a Function won't save
		// you could use Players for function
		stream.storeArgs([enpath(function),enpath(efxFunction) ])
	}
	beatDuration { ^function.tryPerform(\beatDuration) }
	children { ^[function,efxFunction ]}
	*guiClass { ^PlayerEfxFuncGui }

}

// TODO beatDuration for these guys
XFaderPlayer : AbstractPlayer {
	var <>one,<>two,<>xfader,<>pre1,<>pre2;
	*new { arg one,two,xfader,pre1=1.0,pre2=1.0;
		^super.new.one_(this.load(one)).two_(this.load(two))
			.xfader_(this.load(xfader) ?? {KrNumberEditor(0,\unipolar)})
			.pre1_(pre1).pre2_(pre2)
	}
	ar {
		var a,b;
		a = if( pre1 != 1.0,{ one.value * pre1.value },{ one.value });
		b = if( pre2 != 1.0,{ two.value * pre2.value },{ two.value });
		^XFader.ar(a,b,xfader.value)
	}
	beatDuration { 
		var oneb,twob;
		oneb = one.beatDuration;
		twob = two.beatDuration;
		if(oneb.isNil,{
			^twob
		},{
			if(twob.isNil,{
				^oneb
			},{
				^max(oneb,twob)
			})
		})
	}

	children { ^[one,two,xfader,pre1,pre2] }
	storeParamsOn { arg stream;
		stream.storeArgs(enpath([one,two,xfader,pre1,pre2]))
	}
	*guiClass { ^XFaderPlayerGui }
}

XFadeEfxFunc : XFaderPlayer {
	// input, efx
	beatDuration { ^one.tryPerform(\beatDuration) }

	// pre1 is the pre level, pre2 is the post level
	ar { arg newinput;
		var dry,wet;
		dry = (newinput ? one).value;
		// optimizes out if its a simple float
		wet = two.value(if(pre1 == 1.0,{ dry},{ dry * pre1.value }));
		if(pre2 != 1.0,{ wet = wet * pre2.value });
		^XFader.ar(dry,wet,xfader.value)
	}
	

}

XFadePauseEfxFunc : XFadeEfxFunc { // pauses the effect when xfader is 0.0
	// pre1 is the pre level, pre2 is the post level
	ar {
		var dry,wet,controller;
		controller = xfader.value;
		dry = one.value;
		wet = Pause.ar({ two.value(if(pre1 == 1.0,{ dry},{ dry * pre1.value })) },controller);
		if(pre2 != 1.0,{ wet = wet * pre2.value });
		wet = wet * (1.0 - controller);	
		^Mix.ar([dry,wet])//XFader.ar(dry,wet,xfader.value)
	}

}

DualSeriesEfx : AbstractPlayer {
	
	var <input,<efx1,<efx2,<xfader1,<xfader2,<pre1,<pre2,<post;
	var <xf1,<xf2;
	
	*new { arg input,efx1,efx2,xfader1,xfader2,pre1=1.0,pre2=1.0,post=1.0;
		^super.new.deinit(loadDocument(input),loadDocument(efx1),loadDocument(efx2),
				loadDocument(xfader1),loadDocument(xfader2),loadDocument(pre1),loadDocument(pre2),loadDocument(post))
	}
	deinit { arg in,e1,e2,x1,x2,p1,p2,po;
		input = in;
		efx1 = e1;
		efx2 = e2;
		xfader1 = x1;
		xfader2 = x2;
		pre1 = p1;
		pre2 = p2;
		post = po;
		xf1 = XFadeEfxFunc(in,efx1,xfader1,pre1,1.0);
		xf2 = XFadeEfxFunc(xf1,efx2,xfader2,pre2,post);
	}
	ar { arg newinput;
		^xf2.ar( xf1.ar(newinput) )
	}
	children { ^super.children ++ [efx2,xfader2,post] }
	storeParamsOn { arg stream;
		stream.storeArgs(enpath([input,efx1,efx2,xfader1,xfader2,pre1,pre2,post]))
	}
	*guiClass { ^DualSeriesEfxGui }

}

