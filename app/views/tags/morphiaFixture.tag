%{
    if(_delete == 'all') {
        play.test.MorphiaFixtures.deleteAllModels()
    } else if(_delete) {
        play.test.MorphiaFixtures.delete(_delete)
    }
}%

%{
    if(_load) {
        play.test.Fixtures.loadModels(_load)
    }
}%

%{
    if(_arg && _arg instanceof String) {
        try {
            play.Play.classloader.loadClass(_arg).newInstance()
        } catch(Exception e) {
            throw new play.exceptions.TagInternalException('Cannot apply ' + _arg + ' fixture because of ' + e.getClass().getName() + ', ' + e.getMessage())
        }
    }
}%
