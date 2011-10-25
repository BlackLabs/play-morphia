package play.modules.morphia;

import play.modules.morphia.Model.MorphiaQuery;

public enum MorphiaEvent {
    ON_LOAD {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.onLoad((Model)context);
        }
    },LOADED {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.loaded((Model)context);
        }
    },ON_ADD {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.onAdd((Model)context);
        }
    }, ADDED {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.added((Model)context);
        }
    },
    ON_UPDATE {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.onUpdate((Model)context);
        }
    }, UPDATED {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.updated((Model)context);
        }
    }, 
    ON_DELETE {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.onDelete((Model)context);
        }
    }, DELETED {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.deleted((Model)context);
        }
    },
    ON_BATCH_DELETE {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.onBatchDelete((MorphiaQuery)context);
        }
    }, BATCH_DELETED {
        @Override
        public void invokeOn(IMorphiaEventHandler handler, Object context) {
            handler.batchDeleted((MorphiaQuery)context);
        }
    };
    
    public static final String PREFIX = "MORPHIA_";

    private String id_;
    private MorphiaEvent() {
        id_ = PREFIX + name();
    }
    
    public String getId() {
        return id_;
    }
    
    public abstract void invokeOn(IMorphiaEventHandler handler, Object context);
    
    public static MorphiaEvent forId(String name) {
        if (name.startsWith(PREFIX)) name = name.substring(8);
        return MorphiaEvent.valueOf(name);
    }
    
    public static void main(String[] sa) {
        System.out.println(MorphiaEvent.ON_BATCH_DELETE.getId());
        System.out.println(MorphiaEvent.forId("MORPHIA_ON_BATCH_DELETE"));
    }

    public static interface IMorphiaEventHandler {
        void onLoad(Model context);
        void loaded(Model context);
        void onAdd(Model context);
        void onUpdate(Model context);
        void added(Model context);
        void updated(Model context);
        void onDelete(Model context);
        void deleted(Model context);
        void onBatchDelete(MorphiaQuery context);
        void batchDeleted(MorphiaQuery context);
    }
    
    public static class MorphiaEventHandlerAdaptor implements IMorphiaEventHandler {
        @Override
        public void onLoad(Model context) {
        }

        @Override
        public void loaded(Model context) {
        }

        @Override
        public void onAdd(Model context) {
        }

        @Override
        public void onUpdate(Model context) {
        }

        @Override
        public void added(Model context) {
        }

        @Override
        public void updated(Model context) {
        }

        @Override
        public void onDelete(Model context) {
        }

        @Override
        public void deleted(Model context) {
        }

        @Override
        public void onBatchDelete(MorphiaQuery context) {
        }

        @Override
        public void batchDeleted(MorphiaQuery context) {
        }
        
    }
    

}
