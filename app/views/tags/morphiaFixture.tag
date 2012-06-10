#{if _delete == 'all' }
    play.test.MorphiaFixtures.deleteAllModels()
#{/if}
#{elseif _delete}
    play.test.MorphiaFixtures.delete(_delete)
#{/elseif}

#{if _load }
    play.test.Fixtures.loadModels(_load)
#{/if}

#{if _arg && _arg instanceof String }
    try {
        play.Play.classloader.loadClass(_arg).newInstance()
    } catch(Exception e) {
        throw new play.exceptions.TagInternalException('Cannot apply ' + _arg + ' fixture because of ' + e.getClass().getName() + ', ' + e.getMessage())
    }
#{/if}
