### Version
This pipeline is now *0.0.2*
_Target API version: *1.1.3.3*_

### Setup project

Follow skeleton project for setup instructions

### Deployment

This section provides you details on how to provision HMLP on to HOLU for this constituent. Do not forget to add access to event server as;

- Edit `/etc/default/haystack` and add base paths for event server at both announcer and consumer nodes.
    - `holu.base=http://192.168.136.90:7070`

#### Setup event pipeline
The first element is to generate access tokens denoted as prediction pipeline units.

- Execute the following to generate similar content unit
    - `pio app new constituent.recommend-users-for-contexts`
    - Add `--access-key` parameter if you want to control key generated
        - It should be a 64-char string of the form `abcdefghijklmnopqrstuvwxyz1234567890ABCDEFGHIJKLMNOPQRSTUVWXYZ12`
- Record unit ID and access key. You will need this later.

#### Prepare constituent
It is time to prepare constituent unit files that eventually manifests as a HML pipeline.

- Retrieve engine files by cloning relevant git repository
    - `cd /var/lib/haystack/pio/constituents/`
    -  `git@repo.haystack.one:server.tachyon/constituent.recommend-users-for-contexts.git constituent.recommend-users-for-contexts`
    - `cd constituent.recommend-users-for-contexts`
- Change `appName` at `engine.json` to `constituent.recommend-users-for-contexts`
- Edit `/etc/default/haystack` and add access keys to denote addition of HMLP.
    - For **consumer** nodes;
        - `haystack.tachyon.events.dispatch.userstocontext=<accesskey>`
- Complete events import through migration and turning on concomitant consumer

#### Initiate first time training and deploy
It is important to complete at least one iteration of build, train and deploy cycle prior to consumption.

- Build the prediction unit as,
    - `pio build --verbose`
- Train the predictive model as (ensure events migration is complete),
    - `pio train --verbose -v engine.json -- --master spark://monad-dev-vm3:7077 --executor-memory 2G --driver-memory 1G --total-executor-cores 2`
- Deploy the prediction unit as,
    - `mkdir -p /var/log/haystack/pio/deploy/`
    - `vi /var/log/haystack/pio/deploy/17074.log`; save and close
    - `nohup pio deploy -v engine.json --ip 192.168.136.90 --port 17074 --event-server-port 7070 --feedback --accesskey <access_key> -- --master spark://monad-dev-vm3:7077 --executor-memory 2G --driver-memory 1G --total-executor-cores 2 > /var/log/haystack/pio/deploy/17074.log &`
    - Do not kill the deployed process. Subsequent train and deploy would take care of provisioning it again.
    - You can verify deployed HMLP by visiting `http://192.168.136.90:17074/` and querying at `http://192.168.136.90:17074/queries.json `
- Edit `/etc/default/haystack` and add url keys to denote addition of HMLP.
- For **announcer** nodes;
    - `haystack.tachyon.pipeline.access.userstocontext=http://192.168.136.90:17074`

#### Setup consecutive training and deploy
Now that we have successfully provisioned this HMLP; let us set it up for a periodic train-deploy cycle. Note that events are always consumed at real-time but are not accounted for until the next train cycle builds the model.

- Find the accompanying shell scripts of constituent and modify for consumption.
    - Go to constituent directory at;
        - `cd /var/lib/haystack/pio/constituents/constituent.recommend-users-for-contexts/`
    - Time to copy these files to source scripts directory;
        - `mkdir scripts`
        - `cp src/main/resources/scripts/*.sh.template scripts/`
        - `cd scripts/`
    - Rename `local.sh.template` to `local.sh`
        - `mv local.sh.template local.sh`
    - Edit `local.sh` and set the following values;
        - `PIO_HOME=/usr/local/pio`
        - `LOG_DIR=/var/log/haystack/pio/cumulative/17074` (ensure that the path exists)
            - `mkdir -p /var/log/haystack/pio/cumulative/17074`
        - `FROM_EMAIL="info@haystack.one"` (emails are for internal notifications only)
        - `TARGET_EMAIL="masterhank05@gmail.com"` (set this to our support/ customer care email or create a notifications id)
        - `IP=192.168.136.90` - denotes HMLP for queries
    - Rename `redeploy.sh.template` to `Constituent.recommend-users-for-contexts_redeployment_dev.sh`
        - `mv redeploy.sh.template Constituent.recommend-users-for-contexts_redeployment_dev.sh`
    - Edit `Constituent.recommend-users-for-contexts_redeployment_dev.sh` and set the following values;
        - `HOSTNAME=192.168.136.90` (for accessing event server)
        - `PORT=17074` - denotes HMLP port for queries
        - `ACCESSKEY=` - fill this with what was generated earlier
        - `TRAIN_MASTER="spark://monad-dev-vm3:7077"`
        - `DEPLOY_MASTER="spark://monad-dev-vm3:7077"`
    - Do not forget to make it executable;
        - `chmod +x Constituent.recommend-users-for-contexts_redeployment_dev.sh `
    - Adjust spark driver and executor settings as required
    - Ensure `pio build` is run at least once before enabling this script.

Finally, setup crontab for executing these scripts. `mailutils` is used in this script. For Ubuntu, you can do `sudo update-alternatives --config mailx` and see if `/usr/bin/mail.mailutils` is selected.

- Edit crontab file as;
    - `crontab -e` for user level
    - Add the entry as;
        - `0 2,8,14,20 * * * /var/lib/haystack/pio/constituents/constituent.content-similarity/scripts/Constituent.recommend-users-for-contexts_redeployment_dev.sh >/dev/null 2>/dev/null`
        - User `man cron` to check usage
        - Manage schedules in conjunction with all other HMLPs and ensure that trains do not overlap
    - Reload to take effect (optional)
        - `sudo service cron reload`
        - Restart if needed; `sudo systemctl restart cron`

You are all set!
