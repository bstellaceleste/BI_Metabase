(ns metabase.sync
  "Combined functions for running the entire Metabase sync process.
   This delegates to a few distinct steps, which in turn are broken out even further:

   1.  Sync Metadata      (`metabase.sync.sync-metadata`)
   2.  Analysis           (`metabase.sync.analyze`)
   3.  Cache Field Values (`metabase.sync.field-values`)

   In the near future these steps will be scheduled individually, meaning those functions will
   be called directly instead of calling the `sync-database!` function to do all three at once."
  (:require [metabase.sync
             [analyze :as analyze]
             [field-values :as field-values]
             [interface :as i]
             [sync-metadata :as sync-metadata]]
            [schema.core :as s]
            [metabase.sync.util :as sync-util]))

(def ^:private SyncDatabaseOptions
  {(s/optional-key :full-sync?) s/Bool})

(s/defn ^:always-validate sync-database!
  "Perform all the different sync operations synchronously for DATABASE.
   You may optionally supply OPTIONS, which can be used to disable so-called 'full-sync',
   meaning just metadata will be synced, but no 'analysis' (special type determination and
   FieldValues syncing) will be done."
  ([database]
   (sync-database! database {:full-sync? true}))
  ([database :- i/DatabaseInstance, options :- SyncDatabaseOptions]
   (sync-util/sync-operation :sync database (format "Sync %s with options: %s" (sync-util/name-for-logging database) options)
     ;; First make sure Tables, Fields, and FK information is up-to-date
     (sync-metadata/sync-db-metadata! database)
     (when (:full-sync? options)
       ;; Next, run the 'analysis' step where we do things like scan values of fields and update special types accordingly
       (analyze/analyze-db! database)
       ;; Finally, update FieldValues
       (field-values/update-field-values! database)))))


(s/defn ^:always-validate sync-table!
  "Perform all the different sync operations synchronously for a given TABLE."
  [table :- i/TableInstance]
  (sync-metadata/sync-table-metadata! table)
  (analyze/analyze-table! table)
  (field-values/update-field-values-for-table! table))
