@args String cls, Class delete, String load

@{
    if ("all".equals(delete)) {
        play.test.MorphiaFixtures.deleteAllModels();
    } else if (null != delete) {
        play.test.MorphiaFixtures.delete(delete);
    }
}

@{
    if (null != load) {
        play.test.MorphiaFixtures.loadModels(load);
    }
}

@{
    if (null != cls) {
        try {
            play.Play.classloader.loadClass(cls).newInstance();
        } catch(Exception e) {
            throw new play.exceptions.TagInternalException("Cannot apply " + cls + " fixture because of " + e.getClass().getName() + ", " + e.getMessage());
        }
    }
}
